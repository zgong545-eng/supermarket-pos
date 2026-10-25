-- ============================================================
--  V1 · 建表
--  超市收银管理系统 · 43 张表
--
--  约定：
--   · 金额一律 BIGINT，单位「分」，禁止 DECIMAL/FLOAT 存钱
--   · 数量 INT；称重商品重量 DECIMAL(10,3)，单位 kg
--   · 主键 BIGINT AUTO_INCREMENT；业务号另设唯一索引
--   · 时间 DATETIME，由应用写入（不用 ON UPDATE，避免和乐观锁打架）
--   · 逻辑删除统一 deleted TINYINT（0 正常 1 已删）
--   · 不建外键：模块边界靠应用保证，避免插入顺序和锁范围问题
-- ============================================================

-- ==================== M01 平台权限（7 张） ====================

CREATE TABLE sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(50)  NOT NULL                COMMENT '登录名',
    password      VARCHAR(100) NOT NULL                COMMENT 'BCrypt 密文',
    real_name     VARCHAR(50)  NOT NULL                COMMENT '姓名',
    phone         VARCHAR(20)      NULL                COMMENT '手机号',
    email         VARCHAR(100)     NULL                COMMENT '邮箱',
    store_id      BIGINT           NULL                COMMENT '所属门店',
    status        TINYINT      NOT NULL DEFAULT 1      COMMENT '1启用 0停用',
    last_login_at DATETIME         NULL                COMMENT '最后登录时间',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户（经理/收银员/管理员）';

CREATE TABLE sys_role (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    code       VARCHAR(50) NOT NULL COMMENT '角色码：ADMIN/MANAGER/CASHIER',
    name       VARCHAR(50) NOT NULL COMMENT '角色名',
    remark     VARCHAR(200)    NULL,
    is_system  TINYINT     NOT NULL DEFAULT 0 COMMENT '1内置不可删',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色';

CREATE TABLE sys_user_role (
    id      BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联';

CREATE TABLE sys_permission (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(80)  NOT NULL COMMENT '权限码，如 goods:edit',
    name       VARCHAR(80)  NOT NULL COMMENT '权限名',
    type       VARCHAR(10)  NOT NULL DEFAULT 'API' COMMENT 'MENU菜单 BTN按钮 API接口',
    parent_id  BIGINT       NOT NULL DEFAULT 0,
    path       VARCHAR(120)     NULL COMMENT '前端路由',
    sort       INT          NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限点';

CREATE TABLE sys_role_permission (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联';

CREATE TABLE sys_oper_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT           NULL,
    username   VARCHAR(50)      NULL,
    module     VARCHAR(40)  NOT NULL COMMENT '模块：M02 商品 等',
    action     VARCHAR(60)  NOT NULL COMMENT '动作描述',
    method     VARCHAR(10)      NULL COMMENT 'HTTP 方法',
    uri        VARCHAR(200)     NULL,
    params     TEXT             NULL COMMENT '入参（截断 2000 字）',
    ip         VARCHAR(50)      NULL,
    ua         VARCHAR(200)     NULL,
    cost_ms    INT              NULL,
    success    TINYINT      NOT NULL DEFAULT 1,
    error_msg  VARCHAR(500)     NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, created_at),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志';

CREATE TABLE sys_login_log (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    username   VARCHAR(50) NOT NULL,
    ip         VARCHAR(50)     NULL,
    ua         VARCHAR(200)    NULL,
    success    TINYINT     NOT NULL DEFAULT 1,
    msg        VARCHAR(200)    NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志';

-- ==================== M13 系统管理（6 张） ====================

CREATE TABLE sys_store (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    code           VARCHAR(30)  NOT NULL COMMENT '门店编码',
    name           VARCHAR(100) NOT NULL,
    address        VARCHAR(200)     NULL,
    phone          VARCHAR(30)      NULL,
    business_hours VARCHAR(50)      NULL COMMENT '营业时间，如 09:00-22:00',
    status         TINYINT      NOT NULL DEFAULT 1,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店';

CREATE TABLE sys_config (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    config_key   VARCHAR(80)  NOT NULL,
    config_value VARCHAR(500) NOT NULL,
    remark       VARCHAR(200)     NULL,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统参数';

CREATE TABLE sys_dict (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    dict_type  VARCHAR(50) NOT NULL COMMENT '字典类型',
    dict_name  VARCHAR(80) NOT NULL COMMENT '字典名称',
    remark     VARCHAR(200)    NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典';

CREATE TABLE sys_dict_item (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    dict_type  VARCHAR(50) NOT NULL,
    item_value VARCHAR(50) NOT NULL,
    item_label VARCHAR(80) NOT NULL,
    sort       INT         NOT NULL DEFAULT 0,
    status     TINYINT     NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_type_value (dict_type, item_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典项';

CREATE TABLE sys_device (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(40)  NOT NULL COMMENT '设备编号',
    name       VARCHAR(80)  NOT NULL,
    type       VARCHAR(20)  NOT NULL COMMENT 'SCANNER扫码枪 PRINTER小票机 SCALE电子秤 DRAWER钱箱',
    store_id   BIGINT           NULL,
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1在线 0离线',
    remark     VARCHAR(200)     NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='外设登记（课堂演示仅做记录，不接真设备）';

CREATE TABLE sys_sequence (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    seq_key       VARCHAR(20) NOT NULL COMMENT '前缀：SO/PY/RF/IN/LS/CK/SH',
    seq_date      CHAR(8)     NOT NULL COMMENT 'yyyyMMdd',
    current_value INT         NOT NULL DEFAULT 0,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_key_date (seq_key, seq_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单号序列（按天递增，行锁保证不重号）';

-- ==================== M02 商品（4 张） ====================

CREATE TABLE pms_category (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    parent_id  BIGINT      NOT NULL DEFAULT 0,
    name       VARCHAR(60) NOT NULL,
    code       VARCHAR(30)     NULL,
    level      TINYINT     NOT NULL DEFAULT 1,
    path       VARCHAR(200)    NULL COMMENT 'id 路径，如 /1/5/',
    sort       INT         NOT NULL DEFAULT 0,
    status     TINYINT     NOT NULL DEFAULT 1,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品分类';

CREATE TABLE pms_sku (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    sku_code       VARCHAR(40)  NOT NULL COMMENT '商品编码',
    barcode        VARCHAR(40)      NULL COMMENT '主条码',
    name           VARCHAR(120) NOT NULL,
    category_id    BIGINT       NOT NULL,
    spec           VARCHAR(60)      NULL COMMENT '规格，如 500ml',
    unit           VARCHAR(10)  NOT NULL DEFAULT '件',
    brand          VARCHAR(60)      NULL,
    standard_price BIGINT       NOT NULL COMMENT '标准价（分）',
    member_price   BIGINT           NULL COMMENT '会员价（分），空则按等级折扣',
    cost_price     BIGINT       NOT NULL DEFAULT 0 COMMENT '成本价（分）',
    weight_type    VARCHAR(10)  NOT NULL DEFAULT 'PIECE' COMMENT 'PIECE计件 WEIGHT称重',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
    image_url      VARCHAR(300)     NULL,
    remark         VARCHAR(200)     NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_code (sku_code),
    KEY idx_barcode (barcode),
    KEY idx_category (category_id),
    KEY idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品 SKU';

CREATE TABLE pms_sku_barcode (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    sku_id  BIGINT      NOT NULL,
    barcode VARCHAR(40) NOT NULL,
    type    VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL普通 WEIGHT称重码',
    PRIMARY KEY (id),
    UNIQUE KEY uk_barcode (barcode),
    KEY idx_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品条码（一品多码）';

CREATE TABLE pms_price_change (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    sku_id      BIGINT      NOT NULL,
    old_price   BIGINT      NOT NULL,
    new_price   BIGINT      NOT NULL,
    reason      VARCHAR(200)    NULL,
    operator_id BIGINT          NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sku (sku_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='改价记录（谁在什么时候改的价）';

-- ==================== M03 库存（4 张） ====================

CREATE TABLE inv_stock (
    id           BIGINT   NOT NULL AUTO_INCREMENT,
    sku_id       BIGINT   NOT NULL,
    store_id     BIGINT   NOT NULL,
    on_hand      INT      NOT NULL DEFAULT 0 COMMENT '账面总量',
    available    INT      NOT NULL DEFAULT 0 COMMENT '可售',
    locked       INT      NOT NULL DEFAULT 0 COMMENT '已预占（待支付）',
    safety_stock INT      NOT NULL DEFAULT 10 COMMENT '安全库存，低于则预警',
    version      INT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_store (sku_id, store_id),
    KEY idx_warning (available)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存（不变式 on_hand = available + locked）';

CREATE TABLE inv_stock_log (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    sku_id         BIGINT      NOT NULL,
    store_id       BIGINT      NOT NULL,
    change_type    VARCHAR(20) NOT NULL COMMENT 'LOCK预占 RELEASE释放 COMMIT实扣 INBOUND入库 LOSS报损 CHECK盘点',
    qty            INT         NOT NULL COMMENT '变动量（入库为正，出库为负）',
    before_on_hand INT         NOT NULL,
    after_on_hand  INT         NOT NULL,
    biz_no         VARCHAR(40)     NULL COMMENT '关联业务号',
    remark         VARCHAR(200)    NULL,
    operator_id    BIGINT          NULL,
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sku_time (sku_id, created_at),
    KEY idx_biz (biz_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存流水';

CREATE TABLE stock_bill (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    bill_no      VARCHAR(40)  NOT NULL COMMENT '单号 IN/LS/CK+日期+5位',
    type         VARCHAR(20)  NOT NULL COMMENT 'INBOUND补货入库 LOSS报损 CHECK盘点',
    source       VARCHAR(20)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL后台录入 SYSTEM系统生成',
    status       VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED' COMMENT 'DRAFT草稿 CONFIRMED已生效',
    total_qty    INT          NOT NULL DEFAULT 0,
    total_amount BIGINT       NOT NULL DEFAULT 0 COMMENT '成本金额（分）',
    remark       VARCHAR(200)     NULL,
    operator_id  BIGINT           NULL,
    confirmed_at DATETIME         NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bill_no (bill_no),
    KEY idx_type_time (type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存单据（补货/报损/盘点），提交即生效无审批';

CREATE TABLE stock_bill_item (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    bill_id    BIGINT      NOT NULL,
    bill_no    VARCHAR(40) NOT NULL,
    sku_id     BIGINT      NOT NULL,
    qty        INT         NOT NULL,
    cost_price BIGINT      NOT NULL DEFAULT 0,
    amount     BIGINT      NOT NULL DEFAULT 0,
    remark     VARCHAR(200)    NULL,
    PRIMARY KEY (id),
    KEY idx_bill (bill_id),
    KEY idx_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存单据明细';

-- ==================== M04 会员（6 张） ====================

CREATE TABLE mem_level (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    code          VARCHAR(20) NOT NULL,
    name          VARCHAR(40) NOT NULL,
    min_points    INT         NOT NULL DEFAULT 0 COMMENT '升级所需累计积分',
    discount_rate INT         NOT NULL DEFAULT 100 COMMENT '折扣率（90 = 九折）',
    point_rate    INT         NOT NULL DEFAULT 100 COMMENT '积分倍率（100 = 1 倍）',
    sort          INT         NOT NULL DEFAULT 0,
    status        TINYINT     NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员等级';

CREATE TABLE mem_member (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    member_no          VARCHAR(30) NOT NULL COMMENT '会员号',
    card_no            VARCHAR(30)     NULL COMMENT '实体卡号',
    name               VARCHAR(50)     NULL,
    phone              VARCHAR(20) NOT NULL COMMENT '手机号（唯一，报号识别）',
    gender             TINYINT     NOT NULL DEFAULT 0 COMMENT '0未知 1男 2女',
    birthday           DATE            NULL,
    level_id           BIGINT      NOT NULL,
    points             INT         NOT NULL DEFAULT 0 COMMENT '可用积分',
    total_points       INT         NOT NULL DEFAULT 0 COMMENT '累计积分（用于升级）',
    status             TINYINT     NOT NULL DEFAULT 1 COMMENT '1正常 2冻结',
    register_store_id  BIGINT          NULL,
    last_consume_at    DATETIME        NULL,
    created_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_no (member_no),
    UNIQUE KEY uk_phone (phone),
    KEY idx_level (level_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员';

CREATE TABLE mem_point_log (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    member_id     BIGINT      NOT NULL,
    change_type   VARCHAR(20) NOT NULL COMMENT 'EARN获得 USE消费 ADJUST调整 EXPIRE过期',
    points        INT         NOT NULL COMMENT '变动值（可为负）',
    before_points INT         NOT NULL,
    after_points  INT         NOT NULL,
    biz_no        VARCHAR(40)     NULL,
    remark        VARCHAR(200)    NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_member_time (member_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='积分流水';

CREATE TABLE mem_wallet (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    member_id  BIGINT   NOT NULL,
    balance    BIGINT   NOT NULL DEFAULT 0 COMMENT '储值余额（分）',
    version    INT      NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员储值钱包';

CREATE TABLE mem_wallet_txn (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    member_id      BIGINT      NOT NULL,
    txn_type       VARCHAR(20) NOT NULL COMMENT 'RECHARGE充值 CONSUME消费 REFUND退款',
    amount         BIGINT      NOT NULL COMMENT '正数入账，负数出账',
    before_balance BIGINT      NOT NULL,
    after_balance  BIGINT      NOT NULL,
    biz_no         VARCHAR(40)     NULL,
    remark         VARCHAR(200)    NULL,
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_member_time (member_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='储值流水';

CREATE TABLE mem_member_coupon (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    member_id  BIGINT      NOT NULL,
    coupon_id  BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'UNUSED' COMMENT 'UNUSED未用 USED已用 EXPIRED过期',
    receive_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at  DATETIME    NOT NULL,
    used_at    DATETIME        NULL,
    order_no   VARCHAR(40)     NULL,
    PRIMARY KEY (id),
    KEY idx_member_status (member_id, status),
    KEY idx_coupon (coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员持有的券';

-- ==================== M05 促销（4 张） ====================

CREATE TABLE pmt_activity (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    activity_no VARCHAR(30)  NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    type        VARCHAR(30)  NOT NULL COMMENT 'FULL_REDUCE满减 DISCOUNT折扣 NTH_DISCOUNT第N件',
    start_at    DATETIME     NOT NULL,
    end_at      DATETIME     NOT NULL,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    priority    INT          NOT NULL DEFAULT 100 COMMENT '数字小的先算',
    rule_json   VARCHAR(500) NOT NULL COMMENT '规则，如 {"threshold":10000,"reduce":1000,"repeat":false}',
    remark      VARCHAR(200)     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_no (activity_no),
    KEY idx_time (start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='促销活动';

CREATE TABLE pmt_activity_sku (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    sku_id      BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_act_sku (activity_id, sku_id),
    KEY idx_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动适用商品（空集合=全场）';

CREATE TABLE pmt_coupon (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    coupon_code VARCHAR(30) NOT NULL,
    name        VARCHAR(80) NOT NULL,
    type        VARCHAR(20) NOT NULL COMMENT 'CASH满减券 DISCOUNT折扣券',
    face_value  BIGINT      NOT NULL COMMENT 'CASH为面额分；DISCOUNT为折扣率如 88',
    min_amount  BIGINT      NOT NULL DEFAULT 0 COMMENT '使用门槛（分）',
    total_qty   INT         NOT NULL DEFAULT 0 COMMENT '发行总量，0 不限',
    issued_qty  INT         NOT NULL DEFAULT 0 COMMENT '已发放',
    per_limit   INT         NOT NULL DEFAULT 1 COMMENT '每人限领',
    start_at    DATETIME    NOT NULL,
    end_at      DATETIME    NOT NULL,
    valid_days  INT         NOT NULL DEFAULT 0 COMMENT '领取后有效天数，0 则按 end_at',
    status      TINYINT     NOT NULL DEFAULT 1,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_code (coupon_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券模板';

CREATE TABLE pmt_coupon_sku (
    id        BIGINT NOT NULL AUTO_INCREMENT,
    coupon_id BIGINT NOT NULL,
    sku_id    BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_sku (coupon_id, sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='券限定商品（空=全场通用）';

-- ==================== M06 订单（4 张） ====================

CREATE TABLE so_order (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    order_no           VARCHAR(40)  NOT NULL,
    store_id           BIGINT       NOT NULL,
    member_id          BIGINT           NULL,
    cashier_id         BIGINT           NULL,
    shift_id           BIGINT           NULL,
    channel            VARCHAR(10)  NOT NULL DEFAULT 'POS' COMMENT 'POS收银台 H5会员端',
    status             VARCHAR(20)  NOT NULL COMMENT 'CREATED/HOLDING/PENDING_PAY/PAID/COMPLETED/CANCELLED/REFUNDED',
    item_count         INT          NOT NULL DEFAULT 0,
    total_amount       BIGINT       NOT NULL DEFAULT 0 COMMENT '商品原价合计',
    discount_amount    BIGINT       NOT NULL DEFAULT 0 COMMENT '优惠合计',
    payable_amount     BIGINT       NOT NULL DEFAULT 0 COMMENT '应收 = 原价 - 优惠',
    paid_amount        BIGINT       NOT NULL DEFAULT 0 COMMENT '实收',
    change_amount      BIGINT       NOT NULL DEFAULT 0 COMMENT '找零',
    point_used         INT          NOT NULL DEFAULT 0,
    point_deduct_amount BIGINT      NOT NULL DEFAULT 0,
    remark             VARCHAR(200)     NULL,
    version            INT          NOT NULL DEFAULT 0,
    paid_at            DATETIME         NULL,
    finished_at        DATETIME         NULL,
    cancelled_at       DATETIME         NULL,
    cancel_reason      VARCHAR(200)     NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_store_time (store_id, created_at),
    KEY idx_member (member_id, created_at),
    KEY idx_status (status),
    KEY idx_shift (shift_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单主表';

CREATE TABLE so_order_item (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    order_no           VARCHAR(40)  NOT NULL,
    line_no            INT          NOT NULL COMMENT '行号，分摊尾差落在最后一行',
    sku_id             BIGINT       NOT NULL,
    sku_code           VARCHAR(40)  NOT NULL,
    sku_name           VARCHAR(120) NOT NULL,
    barcode            VARCHAR(40)      NULL,
    unit               VARCHAR(10)      NULL,
    standard_price     BIGINT       NOT NULL COMMENT '标准价（分）',
    sale_price         BIGINT       NOT NULL COMMENT '成交单价 = 标准价 - 单品优惠',
    qty                INT          NOT NULL DEFAULT 1,
    weight             DECIMAL(10,3)    NULL COMMENT '称重商品重量',
    amount             BIGINT       NOT NULL COMMENT '行原价 = 标准价 × 数量',
    discount_amount    BIGINT       NOT NULL DEFAULT 0 COMMENT '本行分摊到的优惠合计',
    net_amount         BIGINT       NOT NULL COMMENT '行净额 = amount - discount_amount',
    member_price_used  TINYINT      NOT NULL DEFAULT 0,
    cost_price         BIGINT       NOT NULL DEFAULT 0 COMMENT '成本价快照，用于算毛利',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order (order_no),
    KEY idx_sku_time (sku_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单明细';

CREATE TABLE so_order_discount (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_no    VARCHAR(40)  NOT NULL,
    seq         INT          NOT NULL COMMENT '计算顺序',
    source_type VARCHAR(20)  NOT NULL COMMENT 'MEMBER_PRICE会员价 ACTIVITY活动 COUPON券 POINT积分 MANUAL手工',
    source_no   VARCHAR(40)      NULL COMMENT '活动号/券码',
    name        VARCHAR(80)  NOT NULL,
    amount      BIGINT       NOT NULL COMMENT '本项优惠金额（分）',
    alloc_json  VARCHAR(2000)    NULL COMMENT '按行分摊明细 [{lineNo,skuId,amount}]',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单优惠明细（可追溯每一项优惠从哪来、怎么摊的）';

CREATE TABLE so_order_status_log (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    order_no    VARCHAR(40) NOT NULL,
    from_status VARCHAR(20)     NULL,
    to_status   VARCHAR(20) NOT NULL,
    operator_id BIGINT          NULL,
    remark      VARCHAR(200)    NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单状态流转日志';

-- ==================== M07 支付（3 张） ====================

CREATE TABLE pay_channel (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20) NOT NULL COMMENT 'CASH现金 WECHAT微信 ALIPAY支付宝 WALLET储值 CARD银行卡',
    name        VARCHAR(40) NOT NULL,
    enabled     TINYINT     NOT NULL DEFAULT 1,
    sort        INT         NOT NULL DEFAULT 0,
    config_json VARCHAR(500)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付渠道';

CREATE TABLE pay_payment (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    pay_no           VARCHAR(40) NOT NULL,
    order_no         VARCHAR(40) NOT NULL,
    channel_code     VARCHAR(20) NOT NULL,
    amount           BIGINT      NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'INIT' COMMENT 'INIT待支付 SUCCESS成功 FAILED失败 CLOSED关闭 REFUNDED已退',
    channel_trade_no VARCHAR(64)     NULL COMMENT '渠道流水号，唯一索引做幂等',
    callback_count   INT         NOT NULL DEFAULT 0 COMMENT '回调次数（演示重复回调用）',
    paid_at          DATETIME        NULL,
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pay_no (pay_no),
    UNIQUE KEY uk_channel_trade (channel_trade_no),
    KEY idx_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付单（channel_trade_no 唯一索引 = 幂等的最后一道闸）';

CREATE TABLE pay_refund (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    refund_no         VARCHAR(40)  NOT NULL,
    pay_no            VARCHAR(40)  NOT NULL,
    order_no          VARCHAR(40)  NOT NULL,
    amount            BIGINT       NOT NULL,
    reason            VARCHAR(200)     NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'INIT' COMMENT 'INIT SUCCESS FAILED',
    channel_refund_no VARCHAR(64)      NULL,
    operator_id       BIGINT           NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at       DATETIME         NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    KEY idx_order (order_no),
    KEY idx_pay (pay_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款单';

-- ==================== M08 收银作业（3 张） ====================

CREATE TABLE pos_shift (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    shift_no     VARCHAR(40) NOT NULL,
    store_id     BIGINT      NOT NULL,
    cashier_id   BIGINT      NOT NULL,
    cashier_name VARCHAR(50)     NULL,
    device_code  VARCHAR(40)     NULL,
    start_at     DATETIME    NOT NULL,
    end_at       DATETIME        NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN当班中 CLOSED已交班',
    opening_cash BIGINT      NOT NULL DEFAULT 0 COMMENT '备用金',
    closing_cash BIGINT          NULL COMMENT '实点现金',
    system_cash  BIGINT          NULL COMMENT '系统应收现金',
    cash_diff    BIGINT          NULL COMMENT '长短款 = 实点 - 系统',
    order_count  INT         NOT NULL DEFAULT 0,
    total_amount BIGINT      NOT NULL DEFAULT 0,
    remark       VARCHAR(200)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shift_no (shift_no),
    KEY idx_cashier (cashier_id, start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收银交班';

CREATE TABLE pos_receipt (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    order_no    VARCHAR(40) NOT NULL,
    receipt_no  VARCHAR(40) NOT NULL,
    print_count INT         NOT NULL DEFAULT 1,
    printed_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reprint_by  BIGINT          NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_receipt_no (receipt_no),
    KEY idx_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小票打印记录';

CREATE TABLE pos_hold (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    hold_no      VARCHAR(40) NOT NULL,
    store_id     BIGINT      NOT NULL,
    cashier_id   BIGINT      NOT NULL,
    items_json   TEXT        NOT NULL COMMENT '挂起时的购物车快照',
    total_amount BIGINT      NOT NULL DEFAULT 0,
    status       VARCHAR(20) NOT NULL DEFAULT 'HELD' COMMENT 'HELD挂起中 RESUMED已取出 CANCELLED已作废',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resumed_at   DATETIME        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hold_no (hold_no),
    KEY idx_cashier_status (cashier_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='挂单';

-- ==================== M09 报表（2 张） ====================

CREATE TABLE rpt_daily_sales (
    id                 BIGINT   NOT NULL AUTO_INCREMENT,
    stat_date          DATE     NOT NULL,
    store_id           BIGINT   NOT NULL,
    order_count        INT      NOT NULL DEFAULT 0,
    item_count         INT      NOT NULL DEFAULT 0,
    member_order_count INT      NOT NULL DEFAULT 0,
    total_amount       BIGINT   NOT NULL DEFAULT 0 COMMENT '原价合计',
    discount_amount    BIGINT   NOT NULL DEFAULT 0,
    net_amount         BIGINT   NOT NULL DEFAULT 0 COMMENT '实收合计',
    refund_amount      BIGINT   NOT NULL DEFAULT 0,
    cash_amount        BIGINT   NOT NULL DEFAULT 0,
    wechat_amount      BIGINT   NOT NULL DEFAULT 0,
    alipay_amount      BIGINT   NOT NULL DEFAULT 0,
    wallet_amount      BIGINT   NOT NULL DEFAULT 0,
    cost_amount        BIGINT   NOT NULL DEFAULT 0 COMMENT '成本合计，用于算毛利',
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_date_store (stat_date, store_id),
    KEY idx_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='日销售预聚合（看报表不用现算，答辩演示不卡）';

CREATE TABLE rpt_sku_sales (
    id        BIGINT   NOT NULL AUTO_INCREMENT,
    stat_date DATE     NOT NULL,
    store_id  BIGINT   NOT NULL,
    sku_id    BIGINT   NOT NULL,
    sku_name  VARCHAR(120) NOT NULL,
    qty       INT      NOT NULL DEFAULT 0,
    amount    BIGINT   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_date_store_sku (stat_date, store_id, sku_id),
    KEY idx_date_amount (stat_date, amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品日销（排行用）';
