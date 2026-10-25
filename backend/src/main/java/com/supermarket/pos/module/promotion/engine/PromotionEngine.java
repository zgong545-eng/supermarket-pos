package com.supermarket.pos.module.promotion.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 促销引擎。
 *
 * <p><b>设计要点一：纯函数。</b>这里不碰数据库、不读 Spring 上下文。
 * 输入是"购物车 + 会员 + 可用活动 + 选中券 + 想用多少积分"，输出是"优惠明细 + 每行分摊"。
 * 好处：可以写单元测试把所有奇怪组合跑一遍，而不用起容器、造数据。
 *
 * <p><b>设计要点二：顺序固定。</b>优惠链必须是
 * <pre>标准价 → 会员价 → 活动 → 券 → 积分抵扣</pre>
 * 顺序一变，结果就变。举个例子：100 元的商品，九折活动 + 10 元券。
 * 先打折后减券 = 100×0.9-10 = 80；先减券后打折 = (100-10)×0.9 = 81。
 * 差 1 元，顾客会当场指出来。所以这个顺序写死在代码里，不允许调用方改。
 *
 * <p><b>设计要点三：整单优惠必须摊到每一行。</b>
 * 整单减 10 元，如果不分摊，那么顾客退掉其中一件商品时，
 * 系统就不知道该退多少钱——这是退货纠纷的根源。
 * 分摊规则：按各行"当前可优惠金额"比例分配，分不尽的尾差全部落在最后一行，
 * 保证 {@code Σ各行分摊 = 整单优惠}，一分不差。
 */
@Slf4j
public class PromotionEngine {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 积分兑换比例：多少积分抵 1 元，由配置传入 */
    private final int pointsPerYuan;
    /** 积分最多抵扣订单金额的百分比，由配置传入 */
    private final int maxDeductPercent;

    public PromotionEngine(int pointsPerYuan, int maxDeductPercent) {
        this.pointsPerYuan = Math.max(1, pointsPerYuan);
        this.maxDeductPercent = Math.max(0, Math.min(100, maxDeductPercent));
    }

    // ============================================================
    //  入口
    // ============================================================

    public Result calculate(Context ctx) {
        List<CartLine> lines = ctx.getLines();
        if (lines == null || lines.isEmpty()) {
            return Result.empty();
        }

        // 行号从 1 开始，末行承接尾差时用得上
        for (int i = 0; i < lines.size(); i++) {
            lines.get(i).setLineNo(i + 1);
            lines.get(i).initAmounts();
        }

        List<DiscountItem> discounts = new ArrayList<>();
        long totalAmount = lines.stream().mapToLong(CartLine::getAmount).sum();

        // ---------- 第 1 步：会员价 ----------
        if (ctx.isMember()) {
            applyMemberPrice(lines, ctx.getLevelDiscountRate(), discounts);
        }

        // ---------- 第 2 步：活动（按 priority 从小到大）----------
        List<Activity> activities = new ArrayList<>(ctx.getActivities());
        activities.sort(Comparator.comparingInt(Activity::getPriority));
        for (Activity act : activities) {
            applyActivity(act, lines, discounts);
        }

        // ---------- 第 3 步：优惠券 ----------
        for (Coupon coupon : ctx.getCoupons()) {
            applyCoupon(coupon, lines, discounts);
        }

        long totalDiscount = discounts.stream().mapToLong(DiscountItem::getAmount).sum();
        long payable = Math.max(0, totalAmount - totalDiscount);

        // ---------- 第 4 步：积分抵扣（最后算，因为它直接冲减应付）----------
        int pointUsed = 0;
        long pointAmount = 0;
        if (ctx.isMember() && ctx.getUsePoints() > 0 && payable > 0) {
            long maxByRatio = payable * maxDeductPercent / 100;
            long maxByPoints = (long) ctx.getUsePoints() * 100 / pointsPerYuan;   // 积分→分
            pointAmount = Math.min(Math.min(maxByRatio, maxByPoints), payable);
            // 向下取整到"分"能兑出的整积分，避免出现 0.37 积分这种东西
            pointUsed = (int) (pointAmount * pointsPerYuan / 100);
            pointAmount = (long) pointUsed * 100 / pointsPerYuan;

            if (pointAmount > 0) {
                DiscountItem item = new DiscountItem();
                item.setSourceType("POINT");
                item.setName("积分抵扣");
                item.setAmount(pointAmount);
                // 积分抵扣是全单冲减，按行净额比例摊，规则同整单优惠
                item.setAllocations(allocate(pointAmount, lines));
                discounts.add(item);
                payable -= pointAmount;
            }
        }

        Result result = new Result();
        result.setLines(lines);
        result.setDiscounts(discounts);
        result.setTotalAmount(totalAmount);
        result.setTotalDiscount(totalDiscount + pointAmount);
        result.setPayableAmount(Math.max(0, payable));
        result.setPointUsed(pointUsed);
        result.setPointAmount(pointAmount);
        return result;
    }

    // ============================================================
    //  第 1 步：会员价
    // ============================================================

    /**
     * 会员价优先级：
     * <ol>
     *   <li>商品设了 member_price → 直接用（这是最明确的定价）</li>
     *   <li>没设 → 用标准价 × 等级折扣率</li>
     * </ol>
     * 两者取更低的那个，不会出现"会员价比标价还贵"。
     */
    private void applyMemberPrice(List<CartLine> lines, int levelDiscountRate, List<DiscountItem> discounts) {
        for (CartLine line : lines) {
            long target = line.getStandardPrice();
            if (line.getMemberPrice() != null && line.getMemberPrice() > 0) {
                target = Math.min(target, line.getMemberPrice());
            }
            if (levelDiscountRate > 0 && levelDiscountRate < 100) {
                target = Math.min(target, line.getStandardPrice() * levelDiscountRate / 100);
            }
            if (target < line.getSalePrice()) {
                long diff = (line.getSalePrice() - target) * line.getQty();
                if (diff <= 0) {
                    continue;
                }
                line.setSalePrice(target);
                line.setMemberPriceUsed(true);

                // 会员价是逐行的，不需要分摊，直接把分摊写成单行
                List<Allocation> alloc = new ArrayList<>();
                Allocation a = new Allocation();
                a.setLineNo(line.getLineNo());
                a.setSkuId(line.getSkuId());
                a.setAmount(diff);
                alloc.add(a);

                DiscountItem item = new DiscountItem();
                item.setSourceType("MEMBER_PRICE");
                item.setName("会员价优惠");
                item.setAmount(diff);
                item.setAllocations(alloc);
                discounts.add(item);

                line.applyDiscount(diff);
            }
        }
    }

    // ============================================================
    //  第 2 步：活动
    // ============================================================

    private void applyActivity(Activity act, List<CartLine> lines, List<DiscountItem> discounts) {
        List<CartLine> eligible = lines.stream()
                .filter(l -> l.getCapacity() > 0)
                .filter(l -> act.appliesTo(l.getSkuId()))
                .toList();
        if (eligible.isEmpty()) {
            return;
        }
        long subtotal = eligible.stream().mapToLong(CartLine::getCapacity).sum();
        if (subtotal <= 0) {
            return;
        }

        long discount = 0;
        String name = act.getName();

        try {
            JsonNode rule = act.getRule() == null ? JSON.createObjectNode() : JSON.readTree(act.getRule());
            switch (act.getType()) {
                case "FULL_REDUCE" -> {
                    long threshold = rule.path("threshold").asLong(0);
                    long reduce = rule.path("reduce").asLong(0);
                    boolean repeat = rule.path("repeat").asBoolean(false);
                    if (threshold > 0 && subtotal >= threshold) {
                        discount = repeat ? (subtotal / threshold) * reduce : reduce;
                    }
                }
                case "DISCOUNT" -> {
                    int rate = rule.path("rate").asInt(100);     // 88 = 八八折
                    if (rate > 0 && rate < 100) {
                        discount = subtotal * (100 - rate) / 100;
                    }
                }
                default -> log.debug("暂不支持的活动类型 {}", act.getType());
            }
        } catch (Exception e) {
            log.warn("活动 {} 规则解析失败：{}", act.getActivityNo(), e.getMessage());
            return;
        }

        if (discount <= 0) {
            return;
        }

        List<Allocation> allocations = allocate(discount, eligible);
        long actual = allocations.stream().mapToLong(Allocation::getAmount).sum();

        DiscountItem item = new DiscountItem();
        item.setSourceType("ACTIVITY");
        item.setSourceNo(act.getActivityNo());
        item.setName(name);
        item.setAmount(actual);
        item.setAllocations(allocations);
        discounts.add(item);
    }

    // ============================================================
    //  第 3 步：优惠券
    // ============================================================

    private void applyCoupon(Coupon coupon, List<CartLine> lines, List<DiscountItem> discounts) {
        List<CartLine> eligible = lines.stream()
                .filter(l -> l.getCapacity() > 0)
                .filter(l -> coupon.appliesTo(l.getSkuId()))
                .toList();
        if (eligible.isEmpty()) {
            return;
        }
        long subtotal = eligible.stream().mapToLong(CartLine::getCapacity).sum();
        if (subtotal < coupon.getMinAmount()) {
            return;     // 没到门槛，这张券不参与计算
        }

        long discount = switch (coupon.getType()) {
            case "CASH" -> Math.min(coupon.getFaceValue(), subtotal);
            case "DISCOUNT" -> {
                int rate = (int) coupon.getFaceValue();   // 88 = 八八折
                yield (rate > 0 && rate < 100) ? subtotal * (100 - rate) / 100 : 0;
            }
            default -> 0L;
        };
        if (discount <= 0) {
            return;
        }

        List<Allocation> allocations = allocate(discount, eligible);
        long actual = allocations.stream().mapToLong(Allocation::getAmount).sum();

        DiscountItem item = new DiscountItem();
        item.setSourceType("COUPON");
        item.setSourceNo(coupon.getCouponCode());
        item.setName(coupon.getName());
        item.setAmount(actual);
        item.setAllocations(allocations);
        discounts.add(item);
    }

    // ============================================================
    //  分摊算法（本类的核心）
    // ============================================================

    /**
     * 把整单优惠金额按各行「当前可优惠金额」比例摊到行上。
     *
     * <p>三条硬性要求：
     * <ol>
     *   <li>{@code Σ分摊 == 整单优惠}，一分不能差（否则退货退款对不上）</li>
     *   <li>任何一行分摊不得超过该行当前金额（否则行净额变负数）</li>
     *   <li>结果要稳定：同样的输入必须得到同样的输出，不能因为 Map 遍历顺序而变</li>
     * </ol>
     *
     * <p>做法：先按比例取整，把除不尽的余数按"小数部分从大到小"逐行补齐，
     * 最后还有剩（因为碰到行上限）就顺序找还有空间的行塞进去。
     */
    private List<Allocation> allocate(long total, List<CartLine> eligible) {
        List<Allocation> result = new ArrayList<>();
        if (total <= 0 || eligible.isEmpty()) {
            return result;
        }

        long capSum = eligible.stream().mapToLong(CartLine::getCapacity).sum();
        if (capSum <= 0) {
            return result;
        }
        long actual = Math.min(total, capSum);

        int n = eligible.size();
        long[] alloc = new long[n];
        long assigned = 0;

        if (actual == capSum) {
            // 全额吃掉：直接每行给满，简单不出错
            for (int i = 0; i < n; i++) {
                alloc[i] = eligible.get(i).getCapacity();
            }
            assigned = capSum;
        } else {
            // 按比例
            double[] frac = new double[n];
            for (int i = 0; i < n; i++) {
                long cap = eligible.get(i).getCapacity();
                double raw = (double) actual * cap / capSum;
                long floor = (long) Math.floor(raw);
                alloc[i] = floor;
                frac[i] = raw - floor;
                assigned += floor;
            }
            // 余数按小数部分从大到小分配（稳定：小数相同则按行号）
            long remain = actual - assigned;
            if (remain > 0) {
                Integer[] idx = new Integer[n];
                for (int i = 0; i < n; i++) {
                    idx[i] = i;
                }
                java.util.Arrays.sort(idx, (a, b) -> {
                    int cmp = Double.compare(frac[b], frac[a]);
                    return cmp != 0 ? cmp : Integer.compare(a, b);
                });
                for (int k = 0; k < n && remain > 0; k++) {
                    int i = idx[k];
                    if (alloc[i] < eligible.get(i).getCapacity()) {
                        alloc[i]++;
                        remain--;
                    }
                }
                // 还有剩（说明前面的行都被上限卡住了），顺序补齐
                for (int i = 0; i < n && remain > 0; i++) {
                    long room = eligible.get(i).getCapacity() - alloc[i];
                    if (room > 0) {
                        long add = Math.min(room, remain);
                        alloc[i] += add;
                        remain -= add;
                    }
                }
            }
        }

        for (int i = 0; i < n; i++) {
            if (alloc[i] <= 0) {
                continue;
            }
            CartLine line = eligible.get(i);
            line.applyDiscount(alloc[i]);

            Allocation a = new Allocation();
            a.setLineNo(line.getLineNo());
            a.setSkuId(line.getSkuId());
            a.setAmount(alloc[i]);
            result.add(a);
        }
        return result;
    }

    // ============================================================
    //  数据结构
    // ============================================================

    /** 购物车行。金额全部是「分」。 */
    @Data
    public static class CartLine {
        private int lineNo;
        private Long skuId;
        private String skuCode;
        private String skuName;
        private String barcode;
        private String unit;

        private long standardPrice;
        private Long memberPrice;
        private long costPrice;

        private int qty;
        private java.math.BigDecimal weight;

        /** 行原价 = 标准价 × 数量 */
        private long amount;
        /** 当前成交单价（会员价会改它） */
        private long salePrice;
        /** 本行累计分到的优惠 */
        private long discountAmount;
        /** 本行净额 = amount - discountAmount */
        private long netAmount;

        private boolean memberPriceUsed;
        private String activityNo;

        void initAmounts() {
            this.salePrice = this.standardPrice;
            this.amount = this.standardPrice * this.qty;
            this.discountAmount = 0;
            this.netAmount = this.amount;
        }

        void applyDiscount(long d) {
            this.discountAmount += d;
            this.netAmount -= d;
            if (this.netAmount < 0) {
                this.netAmount = 0;
            }
            if (qty > 0) {
                this.salePrice = this.netAmount / qty;
            }
        }

        /** 这一行现在还能再优惠多少 */
        long getCapacity() {
            return Math.max(0, this.netAmount);
        }
    }

    /** 一项优惠（会员价/活动/券/积分），带按行分摊明细 */
    @Data
    public static class DiscountItem {
        private String sourceType;
        private String sourceNo;
        private String name;
        private long amount;
        private List<Allocation> allocations = new ArrayList<>();
    }

    /** 分摊明细的一行 */
    @Data
    public static class Allocation {
        private int lineNo;
        private Long skuId;
        private long amount;
    }

    /** 活动输入 */
    @Data
    public static class Activity {
        private String activityNo;
        private String name;
        /** FULL_REDUCE / DISCOUNT */
        private String type;
        private int priority = 100;
        private String rule;
        /** 空集合表示全场通用 */
        private List<Long> skuIds = new ArrayList<>();

        public boolean appliesTo(Long skuId) {
            return skuIds == null || skuIds.isEmpty() || skuIds.contains(skuId);
        }
    }

    /** 券输入 */
    @Data
    public static class Coupon {
        private String couponCode;
        private String name;
        /** CASH 满减 / DISCOUNT 折扣 */
        private String type;
        /** CASH：面额（分）；DISCOUNT：折扣率如 88 */
        private long faceValue;
        private long minAmount;
        private List<Long> skuIds = new ArrayList<>();

        public boolean appliesTo(Long skuId) {
            return skuIds == null || skuIds.isEmpty() || skuIds.contains(skuId);
        }
    }

    /** 计算上下文 */
    @Data
    public static class Context {
        private List<CartLine> lines = new ArrayList<>();
        private boolean member;
        private int levelDiscountRate = 100;
        private List<Activity> activities = new ArrayList<>();
        private List<Coupon> coupons = new ArrayList<>();
        private int usePoints = 0;
    }

    /** 计算结果 */
    @Data
    public static class Result {
        private List<CartLine> lines = new ArrayList<>();
        private List<DiscountItem> discounts = new ArrayList<>();
        private long totalAmount;
        private long totalDiscount;
        private long payableAmount;
        private int pointUsed;
        private long pointAmount;

        static Result empty() {
            return new Result();
        }
    }
}
