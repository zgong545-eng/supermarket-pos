-- ============================================================
--  V2 · 基础数据
--
--  只放「系统跑起来必须有」的数据：角色、权限、门店、字典、
--  会员等级、支付渠道、商品分类。管理员账号由 Java 启动时创建
--  （密码要 BCrypt 加密，SQL 里没法算）。
--
--  业务演示数据（60 个 SKU、300 笔订单）不在这里，
--  由 /api/demo/seed 按需灌入，避免污染正式库。
-- ============================================================

-- ---------- 门店 ----------
INSERT INTO sys_store (code, name, address, phone, business_hours) VALUES
('S001', '仓储会员店·中心店', '示范路 88 号', '400-000-0000', '09:00-22:00');

-- ---------- 角色 ----------
INSERT INTO sys_role (code, name, remark, is_system) VALUES
('ADMIN',   '系统管理员', '全部权限，只在后台使用', 1),
('MANAGER', '门店经理',   '除用户管理外的全部权限', 1),
('CASHIER', '收银员',     '只能收银、挂单、交班、查商品库存', 1);

-- ---------- 权限点 ----------
INSERT INTO sys_permission (code, name, type, sort) VALUES
('sys:user',        '用户查看',      'API',  10),
('sys:user:edit',   '用户增改',      'API',  11),
('sys:role',        '角色权限',      'API',  12),
('sys:config',      '系统参数',      'API',  13),
('sys:dict',        '数据字典',      'API',  14),
('sys:store',       '门店管理',      'API',  15),
('sys:device',      '外设管理',      'API',  16),
('sys:log',         '日志查看',      'API',  17),
('goods:view',      '商品查看',      'API',  20),
('goods:edit',      '商品增改',      'API',  21),
('goods:price',     '商品改价',      'API',  22),
('stock:view',      '库存查看',      'API',  30),
('stock:replenish', '补货入库',      'API',  31),
('stock:loss',      '报损',          'API',  32),
('stock:check',     '盘点',          'API',  33),
('member:view',     '会员查看',      'API',  40),
('member:edit',     '会员增改',      'API',  41),
('member:recharge', '会员充值',      'API',  42),
('member:point',    '积分调整',      'API',  43),
('promo:view',      '活动查看',      'API',  50),
('promo:edit',      '活动增改',      'API',  51),
('promo:coupon',    '优惠券管理',    'API',  52),
('order:view',      '订单查看',      'API',  60),
('order:cancel',    '订单取消',      'API',  61),
('order:refund',    '订单退货',      'API',  62),
('pay:view',        '支付查看',      'API',  70),
('pay:refund',      '退款审批',      'API',  71),
('pos:checkout',    '收银结算',      'API',  80),
('pos:hold',        '挂单取单',      'API',  81),
('pos:shift',       '交班',          'API',  82),
('pos:receipt',     '小票补打',      'API',  83),
('report:view',     '报表查看',      'API',  90),
('report:export',   '报表导出',      'API',  91);

-- ---------- 角色授权 ----------
-- 管理员：全部
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.code = 'ADMIN';

-- 经理：除「用户增改」外的全部
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'MANAGER' AND p.code <> 'sys:user:edit';

-- 收银员：只给收银相关的
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'CASHIER'
  AND p.code IN ('goods:view', 'stock:view', 'member:view', 'order:view',
                 'pos:checkout', 'pos:hold', 'pos:shift', 'pos:receipt');

-- ---------- 系统参数 ----------
INSERT INTO sys_config (config_key, config_value, remark) VALUES
('point.exchange_rate',   '100',   '多少积分抵 1 元'),
('point.max_deduct_ratio','30',    '积分最多抵扣订单金额的百分比'),
('order.auto_cancel_min', '15',    '待支付订单超时自动取消（分钟）'),
('stock.warning_enabled', '1',     '是否启用库存预警'),
('receipt.footer',        '感谢光临，欢迎再次购物', '小票页脚'),
('demo.mock_pay_delay',   '0',     '模拟支付回调延迟秒数');

-- ---------- 字典 ----------
INSERT INTO sys_dict (dict_type, dict_name, remark) VALUES
('order_status',   '订单状态', '状态机用'),
('pay_channel',    '支付方式', ''),
('stock_bill_type','库存单据类型', ''),
('promo_type',     '促销类型', '');

INSERT INTO sys_dict_item (dict_type, item_value, item_label, sort) VALUES
('order_status', 'CREATED',     '已建单',   1),
('order_status', 'HOLDING',     '挂单中',   2),
('order_status', 'PENDING_PAY', '待支付',   3),
('order_status', 'PAID',        '已支付',   4),
('order_status', 'COMPLETED',   '已完成',   5),
('order_status', 'CANCELLED',   '已取消',   6),
('order_status', 'REFUNDED',    '已退货',   7),
('pay_channel',  'CASH',        '现金',     1),
('pay_channel',  'WECHAT',      '微信',     2),
('pay_channel',  'ALIPAY',      '支付宝',   3),
('pay_channel',  'WALLET',      '会员储值', 4),
('pay_channel',  'CARD',        '银行卡',   5),
('stock_bill_type', 'INBOUND',  '补货入库', 1),
('stock_bill_type', 'LOSS',     '报损',     2),
('stock_bill_type', 'CHECK',    '盘点',     3),
('promo_type',   'FULL_REDUCE', '满减',     1),
('promo_type',   'DISCOUNT',    '折扣',     2),
('promo_type',   'NTH_DISCOUNT', '第 N 件', 3);

-- ---------- 会员等级 ----------
INSERT INTO mem_level (code, name, min_points, discount_rate, point_rate, sort) VALUES
('NORMAL', '普通会员', 0,    100, 100, 1),
('SILVER', '银卡会员', 2000,  98, 120, 2),
('GOLD',   '金卡会员', 8000,  95, 150, 3);

-- ---------- 支付渠道 ----------
INSERT INTO pay_channel (code, name, enabled, sort) VALUES
('CASH',   '现金',     1, 1),
('WECHAT', '微信支付', 1, 2),
('ALIPAY', '支付宝',   1, 3),
('WALLET', '会员储值', 1, 4),
('CARD',   '银行卡',   1, 5);

-- ---------- 商品分类 ----------
INSERT INTO pms_category (id, parent_id, name, code, level, path, sort) VALUES
(1,  0, '食品饮料', 'FOOD',     1, '/1/',   1),
(2,  0, '生鲜果蔬', 'FRESH',    1, '/2/',   2),
(3,  0, '日用百货', 'DAILY',    1, '/3/',   3),
(4,  0, '粮油调味', 'GRAIN',    1, '/4/',   4),
(11, 1, '乳品',     'DAIRY',    2, '/1/11/', 1),
(12, 1, '休闲零食', 'SNACK',    2, '/1/12/', 2),
(13, 1, '酒水饮料', 'DRINK',    2, '/1/13/', 3),
(21, 2, '蔬菜',     'VEG',      2, '/2/21/', 1),
(22, 2, '水果',     'FRUIT',    2, '/2/22/', 2),
(23, 2, '肉禽蛋',   'MEAT',     2, '/2/23/', 3),
(31, 3, '清洁用品', 'CLEAN',    2, '/3/31/', 1),
(41, 4, '米面',     'RICE',     2, '/4/41/', 1),
(42, 4, '调味品',   'SAUCE',    2, '/4/42/', 2);

-- ---------- 外设登记（课堂演示只做记录） ----------
INSERT INTO sys_device (code, name, type, store_id, status, remark) VALUES
('POS-01',    '1 号收银台',   'SCANNER', 1, 1, '演示用扫码枪'),
('PRT-01',    '小票打印机',   'PRINTER', 1, 1, '58mm 热敏'),
('SCALE-01',  '电子秤',       'SCALE',   1, 1, '生鲜称重'),
('DRAWER-01', '钱箱',         'DRAWER',  1, 1, '');
