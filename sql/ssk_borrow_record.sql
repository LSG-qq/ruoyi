-- ----------------------------------------------------------------------------
-- 借阅记录管理模块（模块名：ssk）初始化脚本
--
-- 【必读】本脚本含中文，必须让连接字符集为 utf8mb4，否则中文会以乱码入库，
--   而且用同样的错误字符集读回来时"看起来又是对的"，极易漏过。
--   本文件第 1 条语句已内置 SET NAMES utf8mb4，正常情况无需额外处理；
--   若你的客户端忽略该语句，请在命令行显式指定：
--     mysql -uroot -p --default-character-set=utf8mb4 < sql/ssk_borrow_record.sql
--   （Windows 下 mysql 客户端默认字符集常为 gbk/936，直接重定向执行必然乱码）
--
-- 内容：1. ssk_borrow_record 建表语句（已存在则跳过）
--       2. 检索索引与「未归还唯一约束」补建（已存在则跳过）
--       3. 借阅记录菜单及按钮权限（菜单ID 2020 ~ 2022）
-- 说明：脚本可重复执行。建表用 IF NOT EXISTS；补列/补索引用 information_schema 判断；
--       菜单先按固定ID清理再插入，均不会产生重复数据。
-- 依赖：1）二级菜单挂在一级目录「图书管理」（menu_id = 2000）下，该目录由
--          sql/ssk_book_category.sql 创建，本脚本内含兜底插入语句。
--       2）书架号字典 book_shelfs 由 sql/ssk_book.sql 创建，本模块直接复用，
--          不重复插入字典数据。
-- 菜单ID：本脚本占用 2020 ~ 2022；清理区间为 2020 ~ 2023 —— 2023 是脚本历史版本
--         用过的「归还图书」，留着是为了让老库重跑时能清掉旧行。
--         不要使用 2010 ~ 2017：该区间由 sql/ssk_book.sql 管理（其 delete 范围为
--         2010 ~ 2017），在本脚本中使用会被重复执行 ssk_book.sql 时误删。
-- ----------------------------------------------------------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、借阅记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ssk_borrow_record` (
`id` int NOT NULL AUTO_INCREMENT COMMENT 'ID',
`book_id` int NOT NULL COMMENT '书籍ID',
`prisoner_number` varchar(255) NOT NULL COMMENT '囚号',
`borrow_time` datetime NOT NULL COMMENT '借出时间',
`return_time` datetime NOT NULL COMMENT '应还时间',
`actual_return_time` datetime DEFAULT NULL COMMENT '实际归还时间',
`created_by` int NOT NULL COMMENT '创建者',
`created_at` datetime NOT NULL COMMENT '创建时间',
`updated_by` int DEFAULT NULL COMMENT '更新者',
`updated_at` datetime DEFAULT NULL COMMENT '更新时间',
`deleted` tinyint(1) NOT NULL COMMENT '是否已删除',
`active_borrow_key` varchar(300) GENERATED ALWAYS AS (case when `deleted` = 0 and `actual_return_time` is null then concat(`book_id`, ':', `prisoner_number`) else null end) STORED COMMENT '未归还记录的唯一键，已归还或已删除时为 NULL',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_ssk_borrow_record_active` (`active_borrow_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='借阅记录';

-- ----------------------------
-- 2、约束与索引补建
--    脚本第 1 段只在建新表时生效，已存在的老库需要在这里补上，逐项判断后动态执行，
--    可安全重复执行。
--    2.1 未归还唯一约束（uk_ssk_borrow_record_active）
--        active_borrow_key 是生成列：只有「未删除且未归还」的记录才会算出值，
--        已归还/已删除时算出 NULL，而 MySQL 唯一索引允许多个 NULL，
--        于是这条约束天然表达「同一囚号对同一本书只能有一条未归还记录」，
--        并且还书后自动释放、可以再次借出。
--        它同时也是业务层「先查后插」重复借阅校验的数据库兜底：
--        两个并发请求同时通过校验时，后写入的那条会被数据库拒绝。
--        注意：若表内已存在重复的未归还记录，加唯一索引会失败，需先人工清理。
--    2.2 idx_ssk_borrow_record_book_id
--        服务于借出前的「同囚号同书未归还」统计（countActiveByBookAndPrisoner）。
--    2.3 idx_ssk_borrow_record_prisoner_number
--        服务于列表按囚号检索。当前查询用的是两侧带通配符的 like（'%囚号%'），
--        这种写法用不上索引；保留索引是为了将来改为前缀匹配（'囚号%'）时不必再改表。
-- ----------------------------
SET @q = CHAR(39);

-- 2.1a 生成列
SET @col_active_exists = (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ssk_borrow_record' AND COLUMN_NAME = 'active_borrow_key'
);
SET @col_active_ddl = IF(
  @col_active_exists = 0,
  CONCAT(
    'ALTER TABLE `ssk_borrow_record` ADD COLUMN `active_borrow_key` varchar(300) GENERATED ALWAYS AS (',
    'case when `deleted` = 0 and `actual_return_time` is null then concat(`book_id`, ', @q, ':', @q, ', `prisoner_number`) else null end',
    ') STORED COMMENT ', @q, '未归还记录的唯一键，已归还或已删除时为 NULL', @q
  ),
  'SELECT 1'
);
PREPARE col_active_stmt FROM @col_active_ddl;
EXECUTE col_active_stmt;
DEALLOCATE PREPARE col_active_stmt;

-- 2.1b 唯一索引
SET @uk_active_exists = (
  SELECT COUNT(1) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ssk_borrow_record' AND INDEX_NAME = 'uk_ssk_borrow_record_active'
);
SET @uk_active_ddl = IF(
  @uk_active_exists = 0,
  'ALTER TABLE `ssk_borrow_record` ADD UNIQUE INDEX `uk_ssk_borrow_record_active` (`active_borrow_key`)',
  'SELECT 1'
);
PREPARE uk_active_stmt FROM @uk_active_ddl;
EXECUTE uk_active_stmt;
DEALLOCATE PREPARE uk_active_stmt;

-- 2.2 书籍ID索引
SET @idx_book_exists = (
  SELECT COUNT(1) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ssk_borrow_record' AND INDEX_NAME = 'idx_ssk_borrow_record_book_id'
);
SET @idx_book_ddl = IF(
  @idx_book_exists = 0,
  'ALTER TABLE `ssk_borrow_record` ADD INDEX `idx_ssk_borrow_record_book_id` (`book_id`)',
  'SELECT 1'
);
PREPARE idx_book_stmt FROM @idx_book_ddl;
EXECUTE idx_book_stmt;
DEALLOCATE PREPARE idx_book_stmt;

-- 2.3 囚号索引
SET @idx_prisoner_exists = (
  SELECT COUNT(1) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ssk_borrow_record' AND INDEX_NAME = 'idx_ssk_borrow_record_prisoner_number'
);
SET @idx_prisoner_ddl = IF(
  @idx_prisoner_exists = 0,
  'ALTER TABLE `ssk_borrow_record` ADD INDEX `idx_ssk_borrow_record_prisoner_number` (`prisoner_number`)',
  'SELECT 1'
);
PREPARE idx_prisoner_stmt FROM @idx_prisoner_ddl;
EXECUTE idx_prisoner_stmt;
DEALLOCATE PREPARE idx_prisoner_stmt;

-- ----------------------------
-- 3、菜单数据清理（按固定ID，保证脚本可重复执行）
-- ----------------------------
delete from sys_menu where menu_id between 2020 and 2023;

-- ----------------------------
-- 4、上级目录兜底：一级菜单「图书管理」（若已由 ssk_book_category.sql 创建则跳过）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select 2000, '图书管理', 0, 5, 'ssk', null, '', '', 1, 0, 'M', '0', '0', '', 'education', 'admin', sysdate(), '', null, '图书管理目录'
from dual where not exists (select 1 from sys_menu where menu_id = 2000);

-- ----------------------------
-- 5、菜单数据
-- ----------------------------
-- 二级菜单：借阅记录
insert into sys_menu values('2020', '借阅记录', '2000', '3', 'borrowRecord', 'ssk/borrowRecord/index', '', '', 1, 0, 'C', '0', '0', 'ssk:borrowRecord:list', 'time', 'admin', sysdate(), '', null, '借阅记录菜单');

-- ----------------------------
-- 6、按钮权限
--    本模块不具备「修改」「删除」能力，页面也只用到「借出」「还书」两个按钮，
--    因此只授权这两个操作。
-- ----------------------------
insert into sys_menu values('2021', '借出图书', '2020', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'ssk:borrowRecord:borrow', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2022', '归还图书', '2020', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'ssk:borrowRecord:return', '#', 'admin', sysdate(), '', null, '');

-- ----------------------------
-- 7、说明
-- ----------------------------
-- 1）超级管理员（user_id = 1）默认拥有全部权限，执行完本脚本后重新登录即可看到「图书管理 / 借阅记录」菜单。
-- 2）其他角色需在「系统管理 - 角色管理 - 修改 - 菜单权限」中勾选「图书管理」下的菜单与按钮后重新登录。
--    若需要把菜单授权给指定角色，可参考下面的语句（role_id 按实际角色修改）：
--    insert into sys_role_menu(role_id, menu_id) values (2, 2000), (2, 2020), (2, 2021), (2, 2022);
-- 3）本模块不提供借阅记录的修改与删除：借阅记录属于业务凭证，产生后不可改写或抹除。
-- 4）借出与归还的库存联动说明：借出时 ssk_book.stock_quantity 减 1（库存为 0 时不允许借出），
--    归还时加 1；两项操作与借阅记录的写入在同一个事务内完成。
-- 5）借阅状态由 SQL 实时计算，无需额外字段：
--    已归还 = actual_return_time 不为空；已逾期 = 未归还且 return_time 早于当前时间；其余为借出中。
-- 6）重复借阅由两层拦截：业务层先查（给出可读提示），数据库唯一约束兜底（防并发穿透）。
