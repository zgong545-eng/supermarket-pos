package com.supermarket.pos.module.product.controller;

import com.supermarket.pos.common.api.PageResult;
import com.supermarket.pos.common.api.Result;
import com.supermarket.pos.module.product.domain.PmsSku;
import com.supermarket.pos.module.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * M02 商品接口。
 *
 * <p>Controller 只做三件事：接参数、调 Service、包 Result。
 * 不写业务判断——这是团队约定里唯一没有例外的规则。
 */
@RestController
@RequestMapping("/api/goods")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('goods:view')")
    public Result<PageResult<PmsSku>> page(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Long categoryId,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size) {
        return Result.ok(productService.page(keyword, categoryId, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:view')")
    public Result<PmsSku> detail(@PathVariable Long id) {
        return Result.ok(productService.detail(id));
    }

    /** 扫码查商品：收银台每扫一次都走这里 */
    @GetMapping("/barcode/{barcode}")
    @PreAuthorize("hasAnyAuthority('goods:view','pos:checkout')")
    public Result<PmsSku> byBarcode(@PathVariable String barcode) {
        return Result.ok(productService.findByBarcode(barcode));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('goods:edit')")
    public Result<Long> create(@RequestBody PmsSku sku) {
        return Result.ok("商品已创建", productService.create(sku));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody PmsSku sku) {
        productService.update(id, sku);
        return Result.ok("商品已更新", null);
    }

    @PutMapping("/{id}/price")
    @PreAuthorize("hasAuthority('goods:price')")
    public Result<Void> changePrice(@PathVariable Long id,
                                    @RequestParam Long newPrice,
                                    @RequestParam(required = false) String reason) {
        productService.changePrice(id, newPrice, reason);
        return Result.ok("改价成功", null);
    }

    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categories() {
        return Result.ok(productService.categoryTree());
    }
}
