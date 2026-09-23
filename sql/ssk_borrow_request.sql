-- ----------------------------------------------------------------------------
-- 借阅申请管理模块（模块名：ssk）初始化脚本
--
-- 【必读】本脚本含中文，必须让连接字符集为 utf8mb4，否则中文会以乱码入库，
--   而且用同样的错误字符集读回来时"看起来又是对的"，极易漏过。
--   本文件第 1 条语句已内置 SET NAMES utf8mb4，正常情况无需额外处理；
--   若你的客户端忽略该语句，请在命令行显式指定：
--     mysql -uroot -p --default-character-set=utf8mb4 < sql/ssk_borrow_request.sql
--   （Windows 下 mysql 客户端默认字符集常为 gbk/936，直接重定向执行必然乱码）
--
-- 内容：1. ssk_borrow_request 建表语句（在需求 DDL 基础上**增加了 prisoner_number（囚号）列**，已存在则跳过）
--       2. 字段约束补丁（status 收紧为 NOT NULL DEFAULT 'ready'、deleted 补 DEFAULT 0、补 prisoner_number 列）
--       3. 检索索引补建（已存在则跳过）
--       4. 借阅申请菜单及按钮权限（菜单ID 2040 ~ 2042）
--       5. （可选）体验用演示数据，默认注释掉
-- 说明：脚本可重复执行。建表用 IF NOT EXISTS；补约束用 MODIFY COLUMN（重复执行不报错）；
--       补索引用 information_schema 判断；菜单先按固定ID清理再插入，均不会产生重复数据。
-- 依赖：1）二级菜单挂在一级目录「图书管理」（menu_id = 2000）下，该目录由
--          sql/ssk_book_category.sql 创建，本脚本内含兜底插入语句。
--       2）书架号字典 book_shelfs 由 sql/ssk_book.sql 创建，本模块直接复用，
--          不重复插入字典数据；申请状态则不走字典，由后端枚举 BorrowRequestStatus 定义。
-- 菜单ID：本脚本占用 2040 ~ 2042；清理区间为 2040 ~ 2045。
--         不要使用 2000 ~ 2023：2000~2005 由 ssk_book_category.sql 管理、
--         2010~2017 由 ssk_book.sql 管理、2020~2023 由 ssk_borrow_record.sql 管理，
--         在别的脚本的清理区间内使用菜单ID，会在重复执行对方脚本时被一起删掉。
-- ----------------------------------------------------------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、借阅申请表
--    记录囚犯在终端发起的借阅申请，管理员在后台逐条审核（同意 / 拒绝）。
--    状态取值 ready-待处理、resolved-已同意、rejected-已拒绝，
--    由后端枚举 com.ruoyi.system.enums.BorrowRequestStatus 统一定义。
--
--    【与需求 DDL 的差异：多了一列 prisoner_number】需求给的 DDL 只有 7 列，没有囚号。
--    原方案是「申请不存囚号，管理员在同意时手输」——在终端接入后这是错的：
--    申请由囚犯在终端提交，终端手里本来就有当前登录的囚号（登录名即囚号），
--    却要管理员照着纸质名册再敲一遍，既多一步人工、又无法核对敲的和对不对，
--    后果是「谁申请的」这个信息彻底丢失。
--    因此按「囚号即登录名」的方案补这一列，由终端写入、后台只读不改。
--    列定义刻意与 ssk_borrow_record.prisoner_number 完全一致（varchar(255) NOT NULL），
--    这样「同意」时把申请行的囚号原样交给借阅记录，两边口径不会分叉。
--    收紧为 NOT NULL 的理由与下面的 status 相同：漏写当场失败，好过静默落一条没有囚号的申请。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ssk_borrow_request` (
`id` int NOT NULL AUTO_INCREMENT COMMENT 'ID',
`book_id` int NOT NULL COMMENT '书籍ID',
`prisoner_number` varchar(255) NOT NULL COMMENT '囚号，申请人的标识，取自终端登录令牌里的用户名（囚号即登录名）',
`borrow_days` int NOT NULL DEFAULT 7 COMMENT '申请借阅天数，囚犯在终端选定的天数，管理员审核时可覆盖',
`status` varchar(20) NOT NULL DEFAULT 'ready' COMMENT '状态，ready-待处理，resolved-已同意，rejected-已拒绝',
`created_at` datetime NOT NULL COMMENT '创建时间',
`updated_by` int DEFAULT NULL COMMENT '更新者',
`updated_at` datetime DEFAULT NULL COMMENT '更新时间',
`deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已删除',
`active_request_key` varchar(300) GENERATED ALWAYS AS (case when `deleted` = 0 and `status` = 'ready' then concat(`book_id`, ':', `prisoner_number`) else null end) STORED COMMENT '待处理申请的唯一键，非待处理或已删除时为 NULL',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_ssk_borrow_request_active` (`active_request_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='借阅申请';

-- ----------------------------
-- 2、字段约束补丁
--    需求给出的 DDL 里 status 允许为 NULL 且无默认值、deleted 无默认值。
--    但这两个字段的实际写入方是终端，而「待处理」是列表的默认视图，两处都要收紧：
--      a) 终端 insert 漏写 status → 库里落 NULL → 列表默认筛「待处理」看不到它；
--      b) 终端显式写 status = NULL → 同样落 NULL，问题一样，而且更隐蔽。
--    落成 NULL 之后这条申请在页面上是**死单**：默认视图看不到，
--    「全部」里能看到但状态列显示为空，点同意/拒绝都会被「申请状态异常」挡回，
--    只能人工改库。实测确认过这个现象。
--    因此这里把 status 收紧为 NOT NULL DEFAULT 'ready'：
--      · 漏写 → 落到「待处理」，能被正常处理；
--      · 显式写 NULL → 严格模式下直接报 1048，终端**当场失败**，比静默产生死单好。
--    【执行顺序】先 UPDATE 清历史 NULL，再 MODIFY 加 NOT NULL。
--    反过来的话，表里还有 NULL 时 MODIFY 在严格模式下会直接报 1048 中断脚本。
--    MODIFY COLUMN 重复执行不会报错（只是把定义再设成同一份），因此无需判断存在性。
--    注意：只调整 default 与 NULL 约束，不改列类型，对已有数据无副作用。
-- ----------------------------
UPDATE `ssk_borrow_request` SET `status` = 'ready' WHERE `status` IS NULL;

ALTER TABLE `ssk_borrow_request`
  MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT 'ready' COMMENT '状态，ready-待处理，resolved-已同意，rejected-已拒绝';

ALTER TABLE `ssk_borrow_request`
  MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已删除';

-- ----------------------------
-- 2.1 补 prisoner_number（囚号）列 —— 老库升级用
--    第 1 段的 CREATE TABLE 只在建新表时生效，已存在的老库拿不到这一列，因此在这里补，
--    判 DDL 用 information_schema，可安全重复执行。
--    【为什么 ADD 里不写 COMMENT】动态语句（PREPARE ... FROM @var）里的中文有被按错误字符集
--    转码的风险，所以动态语句只做纯 ASCII 的加列，中文注释交给紧随其后的**静态** MODIFY 补：
--    静态语句的字符集就是连接字符集，不经过 PREPARE，不会转码。顺带把定义统一成与 CREATE TABLE 一致。
--    ADD 时带 AFTER book_id，让升级库的列顺序与新建库保持一致。
--    已有数据的老库执行 ADD 时，NOT NULL 的 varchar 会被补成空串（隐式默认值）。这类历史申请确实
--    没有囚号，后端在「同意」时会明确拒绝并提示要人工核对，不会静默生成一条没有囚号的借阅记录。
-- ----------------------------
SET @prisoner_col_exists = (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ssk_borrow_request' AND COLUMN_NAME = 'prisoner_number'
);
SET @prisoner_col_ddl = IF(
  @prisoner_col_exists = 0,
  'ALTER TABLE `ssk_borrow_request` ADD COLUMN `prisoner_number` varchar(255) NOT NULL AFTER `book_id`',
  'SELECT 1'
);
PREPARE prisoner_col_stmt FROM @prisoner_col_ddl;
EXECUTE prisoner_col_stmt;
DEALLOCATE PREPARE prisoner_col_stmt;

ALTER TABLE `ssk_borrow_request`
  MODIFY COLUMN `prisoner_number` varchar(255) NOT NULL COMMENT '囚号，申请人的标识，取自终端登录令牌里的用户名（囚号即登录名）';

-- ----------------------------
-- 2.2 补 borrow_days（申请借阅天数）列 —— 老库升级用
--     终端允许囚犯在提交申请时自己选借阅天数，但这个数字原本没有任何列可以承载，
--     不补列就只能丢掉囚犯的选择、或者把天数塞进 remark 这类字段里靠前后端约定解析，
--     两者都会让「囚犯申请时选的天数」在审核页面上无从追溯。
--     NOT NULL DEFAULT 7：老库执行 ADD 时已有行会被填成 7（MySQL 对带默认值的加列会回填默认值），
--     与「历史申请按默认 7 天处理」的语义一致，也不会因为 NOT NULL 在严格模式下中断脚本。
--     写法与 2.1 一致：动态语句只做纯 ASCII 加列，中文注释由紧随其后的静态 MODIFY 补，避开 PREPARE 转码。
-- ----------------------------
SET @borrow_days_col_exists = (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ssk_borrow_request' AND COLUMN_NAME = 'borrow_days'
);
SET @borrow_days_col_ddl = IF(
  @borrow_days_col_exists = 0,
  'ALTER TABLE `ssk_borrow_request` ADD COLUMN `borrow_days` int NOT NULL DEFAULT 7 AFTER `prisoner_number`',
  'SELECT 1'
);
PREPARE borrow_days_col_stmt FROM @borrow_days_col_ddl;
EXECUTE borrow_days_col_stmt;
DEALLOCATE PREPARE borrow_days_col_stmt;

ALTER TABLE `ssk_borrow_request`
  MODIFY COLUMN `borrow_days` int NOT NULL DEFAULT 7 COMMENT '申请借阅天数，囚犯在终端选定的天数，管理员审核时可覆盖';

-- ----------------------------
-- 2.3 补 active_request_key（待处理唯一键）生成列 —— 老库升级用
--     用于表达「同一囚号对同一本书只能有一条待处理申请」，约束落在数据库而不是只靠业务层先查：
--     终端上囚犯容易连点提交，两个请求同时通过「先查」再各插一条，先查是拦不住的（实测过同类场景）。
--     生成列只在「未删除且待处理」时算出值，一旦被同意/拒绝或逻辑删除就变成 NULL，
--     而 MySQL 唯一索引允许多个 NULL —— 于是这条约束天然表达「只能有一条待处理」，
--     并且审核完成后自动释放，同一囚号可以再次申请同一本书。
--     与 ssk_borrow_record.active_borrow_key 是同一套手法，两处口径保持一致。
--     列宽 300 足够：book_id 最多 11 位 + 1 位分隔符 + 囚号 varchar(255)。
-- ----------------------------
SET @active_key_col_exists = (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ssk_borrow_request' AND COLUMN_NAME = 'active_request_key'
);
SET @active_key_col_ddl = IF(
  @active_key_col_exists = 0,
  'ALTER TABLE `ssk_borrow_request` ADD COLUMN `active_request_key` varchar(300) GENERATED ALWAYS AS (case when `deleted` = 0 and `status` = ''ready'' then concat(`book_id`, '':'', `prisoner_number`) else null end) STORED',
  'SELECT 1'
);
PREPARE active_key_col_stmt FROM @active_key_col_ddl;
EXECUTE active_key_col_stmt;
DEALLOCATE PREPARE active_key_col_stmt;

ALTER TABLE `ssk_borrow_request`
  MODIFY COLUMN `active_request_key` varchar(300) GENERATED ALWAYS AS (case when `deleted` = 0 and `status` = 'ready' then concat(`book_id`, ':', `prisoner_number`) else null end) STORED COMMENT '待处理申请的唯一键，非待处理或已删除时为 NULL';

-- ----------------------------
-- 3、索引补建
--    脚本第 1 段只在建新表时生效，已存在的老库需要在这里补上，逐项判断后动态执行，
--    可安全重复执行。
--    3.1 idx_ssk_borrow_request_book_id
--        服务于列表左连接 ssk_book 取书籍名称与书架号（left join b on b.id = r.book_id）。
--    3.2 idx_ssk_borrow_request_status_created_at
--        服务于默认视图：按状态过滤 + 按申请时间倒序。
--        列表默认筛「待处理」，是最常用的入口；把 status 放前、created_at 放后，
--        既能命中过滤，也能让同一个索引顺带完成排序，避免额外的一次排序操作。
--    3.3 idx_ssk_borrow_request_prisoner_number_created_at
--        服务于按囚号查本人的申请（终端的「我的借阅申请」），说明见下方 3.3 段。
-- ----------------------------
SET @idx_book_exists = (
  SELECT COUNT(1) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ssk_borrow_request' AND INDEX_NAME = 'idx_ssk_borrow_request_book_id'
);
SET @idx_book_ddl = IF(
  @idx_book_exists = 0,
  'ALTER TABLE `ssk_borrow_request` ADD INDEX `idx_ssk_borrow_request_book_id` (`book_id`)',
  'SELECT 1'
);
PREPARE idx_book_stmt FROM @idx_book_ddl;
EXECUTE idx_book_stmt;
DEALLOCATE PREPARE idx_book_stmt;

SET @idx_status_exists = (
  SELECT COUNT(1) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ssk_borrow_request' AND INDEX_NAME = 'idx_ssk_borrow_request_status_created_at'
);
SET @idx_status_ddl = IF(
  @idx_status_exists = 0,
  'ALTER TABLE `ssk_borrow_request` ADD INDEX `idx_ssk_borrow_request_status_created_at` (`status`, `created_at`)',
  'SELECT 1'
);
PREPARE idx_status_stmt FROM @idx_status_ddl;
EXECUTE idx_status_stmt;
DEALLOCATE PREPARE idx_status_stmt;

-- ----------------------------
-- 3.3 idx_ssk_borrow_request_prisoner_number_created_at
--    服务于「按囚号查本人的申请」：终端的「我的借阅申请」就是
--    where prisoner_number = ? order by created_at desc，后台按囚号筛选也走同一条。
--    囚号放前、created_at 放后，过滤与排序由同一个索引一次完成，避免额外的一次排序。
--    列宽与 ssk_borrow_record 上的囚号索引一致（全列索引，varchar(255) 在 utf8mb4 下 1020 字节，
--    远小于 InnoDB 的 3072 字节上限），不额外截前缀，免得前缀索引在等值查询上失去区分度。
-- ----------------------------
SET @idx_prisoner_exists = (
  SELECT COUNT(1) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ssk_borrow_request'
    AND INDEX_NAME = 'idx_ssk_borrow_request_prisoner_number_created_at'
);
SET @idx_prisoner_ddl = IF(
  @idx_prisoner_exists = 0,
  'ALTER TABLE `ssk_borrow_request` ADD INDEX `idx_ssk_borrow_request_prisoner_number_created_at` (`prisoner_number`, `created_at`)',
  'SELECT 1'
);
PREPARE idx_prisoner_stmt FROM @idx_prisoner_ddl;
EXECUTE idx_prisoner_stmt;
DEALLOCATE PREPARE idx_prisoner_stmt;

-- ----------------------------
-- 3.4 uk_ssk_borrow_request_active（待处理申请唯一约束）
--     承载「同一囚号对同一本书只能有一条待处理申请」，是业务层「先查后插」的数据库兜底，
--     具体原理见第 2.3 段。走生成列 active_request_key，因此不影响已处理/已删除的历史行。
--
--     【为什么创建前要先判重】老库里可能已经存在同一 book_id + prisoner_number 的多条 ready 申请
--     （约束之前提交的数据）。这种数据下直接 ADD UNIQUE INDEX 会报 1062 中断整个脚本，
--     后面还没执行的菜单、索引全被跳过，而报错信息只指向某个主键值、看不出根因。
--     因此这里先统计重复组，只有「索引不存在且没有重复数据」时才创建；
--     存在重复时不做任何自动删除（删哪些、留哪条属于业务判断，脚本不替使用者决定），
--     只打印一条 ASCII 提示要求人工处理后再重跑。提示刻意用英文：动态语句里的中文会被按
--     错误字符集转码，而这条提示恰恰是乱码时最需要看清楚的一条。
-- ----------------------------
SET @uk_active_exists = (
  SELECT COUNT(1) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ssk_borrow_request'
    AND INDEX_NAME = 'uk_ssk_borrow_request_active'
);
SET @dup_ready_group_count = (
  SELECT COUNT(1) FROM (
    SELECT 1 FROM `ssk_borrow_request`
    WHERE `deleted` = 0 AND `status` = 'ready'
    GROUP BY `book_id`, `prisoner_number`
    HAVING COUNT(1) > 1
  ) AS dup_ready_group
);
SET @uk_active_ddl = IF(
  @uk_active_exists = 0 AND @dup_ready_group_count = 0,
  'ALTER TABLE `ssk_borrow_request` ADD UNIQUE INDEX `uk_ssk_borrow_request_active` (`active_request_key`)',
  IF(@uk_active_exists = 1,
     'SELECT ''uk_ssk_borrow_request_active already exists, skipped'' AS result',
     'SELECT ''WARNING: duplicate ready requests exist, unique index uk_ssk_borrow_request_active NOT created. Keep only one ready row per (book_id, prisoner_number), then re-run this script.'' AS warning')
);
PREPARE uk_active_stmt FROM @uk_active_ddl;
EXECUTE uk_active_stmt;
DEALLOCATE PREPARE uk_active_stmt;

-- ----------------------------
-- 4、菜单数据清理（按固定ID，保证脚本可重复执行）
-- ----------------------------
delete from sys_menu where menu_id between 2040 and 2045;

-- ----------------------------
-- 5、上级目录兜底：一级菜单「图书管理」（若已由 ssk_book_category.sql 创建则跳过）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select 2000, '图书管理', 0, 5, 'ssk', null, '', '', 1, 0, 'M', '0', '0', '', 'education', 'admin', sysdate(), '', null, '图书管理目录'
from dual where not exists (select 1 from sys_menu where menu_id = 2000);

-- ----------------------------
-- 6、菜单数据
-- ----------------------------
-- 二级菜单：借阅申请（排列在「图书类目 / 图书信息 / 借阅记录」之后）
insert into sys_menu values('2040', '借阅申请', '2000', '4', 'borrowRequest', 'ssk/borrowRequest/index', '', '', 1, 0, 'C', '0', '0', 'ssk:borrowRequest:list', 'message', 'admin', sysdate(), '', null, '借阅申请菜单');

-- ----------------------------
-- 7、按钮权限
--    本模块只提供「查看」「同意」「拒绝」三类能力，没有新增、修改与删除
--    （申请由终端写入，后台只改状态），因此只授权这三个操作。
-- ----------------------------
insert into sys_menu values('2041', '同意申请', '2040', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'ssk:borrowRequest:approve', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2042', '拒绝申请', '2040', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'ssk:borrowRequest:reject',  '#', 'admin', sysdate(), '', null, '');

-- ----------------------------
-- 8、（可选）体验用演示数据 —— 默认注释掉，需要看效果时自行取消注释
--    正式环境不要执行：申请由囚犯在终端提交，后台不应造业务数据。
--    取值方式：取一本未删除的书籍插一条待处理申请，没有书籍时不会插入任何行。
--    囚号这里写死成演示号 20260001：真实场景下它由终端从登录令牌里取，这里只是让后台页面有数据可看。
--    没有这条演示账号也不影响插入（本表不校验囚号是否存在，它与 sys_user 之间没有外键）。
-- ----------------------------
-- insert into ssk_borrow_request (book_id, prisoner_number, status, created_at, deleted)
-- select b.id, '20260001', 'ready', sysdate(), 0
-- from ssk_book b
-- where b.deleted = 0
-- order by b.id
-- limit 1;

-- ----------------------------
-- 9、说明
-- ----------------------------
-- 1）超级管理员（user_id = 1）默认拥有全部权限，执行完本脚本后重新登录即可看到「图书管理 / 借阅申请」菜单。
--    若菜单没出现，先确认本脚本是否真的执行成功（查 sys_menu 里 2040 是否存在），
--    再回浏览器按 F5 刷新（路由缓存在前端内存里，刷新或重新登录才会重新拉 /getRouters）。
-- 2）其他角色需在「系统管理 - 角色管理 - 修改 - 菜单权限」中勾选「图书管理」下的菜单与按钮后重新登录。
--    若需要把菜单授权给指定角色，可参考下面的语句（role_id 按实际角色修改）：
--    insert into sys_role_menu(role_id, menu_id) values (2, 2000), (2, 2040), (2, 2041), (2, 2042);
-- 3）申请状态不走字典表：取值与中文标签由后端枚举 BorrowRequestStatus 统一定义，
--    前端下拉选项通过 /ssk/borrowRequest/statusOptions 获取，避免字典与枚举两处口径不一致。
--    注意 status 列为 NOT NULL DEFAULT 'ready'：终端 insert 漏写会落到「待处理」，
--    显式写 NULL 会被数据库直接拒绝（错误 1048）。这是刻意收紧的，见第 2 段的说明。
-- 4）同意申请会在同一个事务内完成三件事：把申请状态改为 resolved、生成一条借阅记录、扣减书籍库存。
--    任一步失败整体回滚，申请回到待处理，不会出现「书借出去了申请还是待处理」。
--    生成的借阅记录，其囚号**直接取自申请行**（prisoner_number），后台不需要也无法再手输，
--    因此借阅记录上的借阅人一定就是当初提交申请的那个囚号，不会出现「张冠李戴」。
-- 5）同意与拒绝都只允许处理「待处理」的申请：SQL 的更新语句自带 status = 'ready' 条件，
--    既是重复提交的保护，也是两个管理员同时操作时的并发兜底（只有一个能成功）。
-- 6）本模块不提供申请的删除。deleted 列由终端侧维护，后台查询一律带 deleted = 0。
-- 7）prisoner_number 由终端在提交申请时写入（取值＝当前登录账号的用户名，即囚号），后台只读不改。
--    该列 NOT NULL：终端漏写会当场报错（严格模式 1364），好过静默落一条没有囚号的申请。
--    老库升级时该列会被补成空串，这类历史申请无法确定借阅人，「同意」时后端会明确拒绝并提示人工核对。
-- 8）borrow_days 由囚犯在终端提交申请时选定，管理员在「同意」时可覆盖：
--    同意时不传天数 → 取申请行里的 borrow_days；传了则以传入值为准。
--    上下限由后端 BorrowRequestBiz 统一下发（/client/borrowRequest/borrowDayLimits），
--    前端不写死数字，避免「界面允许填的天数被后端拒绝」。
--    老库补列时历史申请被填成 7，与原有的「默认 7 天」语义一致，行为不变。
-- 9）uk_ssk_borrow_request_active 表达「同一囚号对同一本书只能有一条待处理申请」：
--    生成列 active_request_key 只在「未删除且 status = 'ready'」时算出值，其余为 NULL，
--    唯一索引允许多个 NULL，因此审核完成后自动释放，同一囚号可以再次申请同一本书。
--    它是业务层「先查后插」的并发兜底（终端上连点提交时先查拦不住）。
--    若本脚本执行时库里已存在重复的待处理申请，索引会被**跳过**并打印一条英文警告，
--    需人工保留一条后重跑；脚本不会自动删除业务数据。
-- 10）申请状态与图书库存同为「提交时的判断依据」：库存为 0 时终端会拒绝提交。
