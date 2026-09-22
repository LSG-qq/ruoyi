-- ----------------------------------------------------------------------------
-- 图书分类管理模块（模块名：ssk）初始化脚本
-- 内容：1. ssk_book_category 建表语句  2. 历史表升级语句  3. 图书类目菜单及权限标识
-- 说明：脚本可重复执行。建表用 IF NOT EXISTS，升级用 information_schema 判断列是否存在，
--       菜单部分先按固定ID清理历史数据再插入，均不会产生重复数据。
-- 菜单ID占用 2000 ~ 2005，与若依内置菜单（1 ~ 1060）不冲突。
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、图书分类表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ssk_book_category` (
`id` int NOT NULL AUTO_INCREMENT COMMENT 'ID',
`parent_id` int DEFAULT NULL COMMENT '上级ID',
`order_num` int NOT NULL DEFAULT 0 COMMENT '显示排序',
`title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标题',
`remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
`created_by` int NOT NULL COMMENT '创建人',
`created_at` datetime NOT NULL COMMENT '创建时间',
`updated_by` int DEFAULT NULL COMMENT '更新人',
`updated_at` datetime DEFAULT NULL COMMENT '更新时间',
`deleted` tinyint(1) NOT NULL COMMENT '是否已删除',
PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书分类';

-- ----------------------------
-- 2、历史表升级：补充显示排序字段
--    若表已按旧脚本创建，下面的语句会自动补列；列已存在时不做任何操作，可安全重复执行。
-- ----------------------------
SET @order_num_exists = (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ssk_book_category' AND COLUMN_NAME = 'order_num'
);
SET @order_num_ddl = IF(
  @order_num_exists = 0,
  'ALTER TABLE `ssk_book_category` ADD COLUMN `order_num` int NOT NULL DEFAULT 0 COMMENT ''显示排序'' AFTER `parent_id`',
  'SELECT 1'
);
PREPARE order_num_stmt FROM @order_num_ddl;
EXECUTE order_num_stmt;
DEALLOCATE PREPARE order_num_stmt;

-- ----------------------------
-- 3、菜单数据清理（按固定ID，保证脚本可重复执行）
-- ----------------------------
delete from sys_menu where menu_id between 2000 and 2005;

-- ----------------------------
-- 4、菜单数据
-- ----------------------------
-- 一级菜单：图书管理目录
insert into sys_menu values('2000', '图书管理', '0', '5', 'ssk', null, '', '', 1, 0, 'M', '0', '0', '', 'education', 'admin', sysdate(), '', null, '图书管理目录');

-- 二级菜单：图书类目
insert into sys_menu values('2001', '图书类目', '2000', '1', 'bookCategory', 'ssk/bookCategory/index', '', '', 1, 0, 'C', '0', '0', 'ssk:bookCategory:list', 'tree-table', 'admin', sysdate(), '', null, '图书类目菜单');

-- ----------------------------
-- 5、按钮权限
-- ----------------------------
insert into sys_menu values('2002', '类目查询', '2001', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'ssk:bookCategory:query',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2003', '类目新增', '2001', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'ssk:bookCategory:add',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2004', '类目修改', '2001', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'ssk:bookCategory:edit',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2005', '类目删除', '2001', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'ssk:bookCategory:remove', '#', 'admin', sysdate(), '', null, '');

-- ----------------------------
-- 6、说明
-- ----------------------------
-- 超级管理员（user_id = 1）默认拥有全部权限，执行完本脚本后重新登录即可看到「图书管理 / 图书类目」菜单。
-- 其他角色需在「系统管理 - 角色管理 - 修改 - 菜单权限」中勾选「图书管理」下的菜单与按钮后重新登录。
-- 若需要把菜单授权给指定角色，可参考下面的语句（role_id 按实际角色修改）：
-- insert into sys_role_menu(role_id, menu_id) values (2, 2000), (2, 2001), (2, 2002), (2, 2003), (2, 2004), (2, 2005);
