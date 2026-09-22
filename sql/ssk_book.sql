-- ----------------------------------------------------------------------------
-- 图书管理模块（模块名：ssk）初始化脚本
-- 内容：1. ssk_book 建表语句
--       2. 图书信息菜单及按钮权限（菜单ID 2010 ~ 2014）
--       3. 书架号字典类型 book_shelfs 及字典数据
-- 说明：脚本可重复执行。建表用 IF NOT EXISTS；菜单按固定ID先清理再插入；
--       字典按 dict_type 先清理再插入，均不会产生重复数据。
-- 依赖：本脚本中的二级菜单挂在一级目录「图书管理」（menu_id = 2000）下，
--       该目录由 sql/ssk_book_category.sql 创建，本脚本内含兜底插入语句，
--       即使未执行过 ssk_book_category.sql 也能正常挂载。
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、图书表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ssk_book` (
`id` int NOT NULL AUTO_INCREMENT COMMENT 'ID',
`name` varchar(255) NOT NULL COMMENT '书籍名称',
`description` varchar(500) NOT NULL COMMENT '书籍描述',
`stock_quantity` int NOT NULL DEFAULT '1' COMMENT '库存',
`author` varchar(255) NOT NULL COMMENT '作者',
`category_ids` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '类目ID集',
`shelf_code` varchar(255) NOT NULL COMMENT '书架号',
`cover` varchar(255) DEFAULT NULL COMMENT '封面',
`images` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '图片集',
`created_by` int NOT NULL COMMENT '创建人ID',
`created_at` datetime NOT NULL COMMENT '创建时间',
`updated_by` int DEFAULT NULL COMMENT '更新者',
`updated_at` datetime DEFAULT NULL COMMENT '更新时间',
`deleted` tinyint(1) NOT NULL COMMENT '是否已删除',
PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书';

-- 常用检索字段索引：类目筛选走 find_in_set，书架号为等值查询
-- 重复执行时若索引已存在会报 Duplicate key name，此处用 information_schema 判断后动态创建
SET @idx_shelf_exists = (
  SELECT COUNT(1) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ssk_book' AND INDEX_NAME = 'idx_ssk_book_shelf_code'
);
SET @idx_shelf_ddl = IF(
  @idx_shelf_exists = 0,
  'ALTER TABLE `ssk_book` ADD INDEX `idx_ssk_book_shelf_code` (`shelf_code`)',
  'SELECT 1'
);
PREPARE idx_shelf_stmt FROM @idx_shelf_ddl;
EXECUTE idx_shelf_stmt;
DEALLOCATE PREPARE idx_shelf_stmt;

-- ----------------------------
-- 2、菜单数据清理（按固定ID，保证脚本可重复执行）
-- ----------------------------
delete from sys_menu where menu_id between 2010 and 2017;

-- ----------------------------
-- 3、上级目录兜底：一级菜单「图书管理」（若已由 ssk_book_category.sql 创建则跳过）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select 2000, '图书管理', 0, 5, 'ssk', null, '', '', 1, 0, 'M', '0', '0', '', 'education', 'admin', sysdate(), '', null, '图书管理目录'
from dual where not exists (select 1 from sys_menu where menu_id = 2000);

-- ----------------------------
-- 4、菜单数据
-- ----------------------------
-- 二级菜单：图书信息
insert into sys_menu values('2010', '图书信息', '2000', '2', 'book', 'ssk/book/index', '', '', 1, 0, 'C', '0', '0', 'ssk:book:list', 'table', 'admin', sysdate(), '', null, '图书信息菜单');

-- ----------------------------
-- 5、按钮权限
-- ----------------------------
insert into sys_menu values('2011', '图书查询', '2010', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'ssk:book:query',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2012', '图书新增', '2010', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'ssk:book:add',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2013', '图书修改', '2010', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'ssk:book:edit',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2014', '图书删除', '2010', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'ssk:book:remove', '#', 'admin', sysdate(), '', null, '');

-- ----------------------------
-- 6、字典类型：书架号
-- ----------------------------
delete from sys_dict_data where dict_type = 'book_shelfs';
delete from sys_dict_type where dict_type = 'book_shelfs';

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, update_by, update_time, remark)
values ('图书书架号', 'book_shelfs', '0', 'admin', sysdate(), '', null, '图书书架号列表');

-- ----------------------------
-- 7、字典数据：书架号（示例数据，可在「系统管理 - 字典管理」中自行增删改）
-- ----------------------------
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark) values
(1, 'B001', 'B001', 'book_shelfs', '', '', 'Y', '0', 'admin', sysdate(), '', null, '书架号B001'),
(2, 'B002', 'B002', 'book_shelfs', '', '', 'N', '0', 'admin', sysdate(), '', null, '书架号B002'),
(3, 'B003', 'B003', 'book_shelfs', '', '', 'N', '0', 'admin', sysdate(), '', null, '书架号B003'),
(4, 'B004', 'B004', 'book_shelfs', '', '', 'N', '0', 'admin', sysdate(), '', null, '书架号B004'),
(5, 'B005', 'B005', 'book_shelfs', '', '', 'N', '0', 'admin', sysdate(), '', null, '书架号B005'),
(6, 'B006', 'B006', 'book_shelfs', '', '', 'N', '0', 'admin', sysdate(), '', null, '书架号B006'),
(7, 'B007', 'B007', 'book_shelfs', '', '', 'N', '0', 'admin', sysdate(), '', null, '书架号B007'),
(8, 'B008', 'B008', 'book_shelfs', '', '', 'N', '0', 'admin', sysdate(), '', null, '书架号B008');

-- ----------------------------
-- 8、说明
-- ----------------------------
-- 1）超级管理员（user_id = 1）默认拥有全部权限，执行完本脚本后重新登录即可看到「图书管理 / 图书信息」菜单。
-- 2）其他角色需在「系统管理 - 角色管理 - 修改 - 菜单权限」中勾选「图书管理」下的菜单与按钮后重新登录。
--    若需要把菜单授权给指定角色，可参考下面的语句（role_id 按实际角色修改）：
--    insert into sys_role_menu(role_id, menu_id) values (2, 2000), (2, 2010), (2, 2011), (2, 2012), (2, 2013), (2, 2014);
-- 3）ssk_book.category_ids 存放逗号分隔的类目ID（如 '1,5,9'），图书的封面 cover 由图片集 images 的第一张图派生。
