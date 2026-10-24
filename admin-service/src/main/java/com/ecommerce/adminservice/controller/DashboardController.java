package com.ecommerce.adminservice.controller;

import com.ecommerce.adminservice.dto.response.ActivityItem;
import com.ecommerce.adminservice.dto.response.ApiResult;
import com.ecommerce.adminservice.dto.response.DailySales;
import com.ecommerce.adminservice.dto.response.DashboardSummary;
import com.ecommerce.adminservice.dto.response.ErrorResponse;
import com.ecommerce.adminservice.dto.response.LowStockItem;
import com.ecommerce.adminservice.dto.response.PageResponse;
import com.ecommerce.adminservice.dto.response.PipelineStage;
import com.ecommerce.adminservice.dto.response.TopProduct;
import com.ecommerce.adminservice.report.ReportPeriod;
import com.ecommerce.adminservice.report.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * The admin dashboard (ADMIN only). Date ranges: from / to (yyyy-MM-dd, both included, in the shop's time zone);
 * without them, the last 30 days.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard (admin)", description = "Sales, orders, payments, stock and deliveries at a glance (ADMIN)")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid dates, range too long or invalid parameter",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Not logged in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Not an ADMIN",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class DashboardController {

    private final ReportService reports;

    @Operation(summary = "Summary", description = "Gross / net sales, refunds, average order value, units, change against the "
            + "previous period; orders placed, paid, cancelled, shipped, delivered; payment success rate; buyers and new buyers; "
            + "and what's waiting right now (to ship, in transit, low stock, missed deliveries).")
    @GetMapping("/summary")
    public ResponseEntity<ApiResult<DashboardSummary>> summary(
            @Parameter(example = "2026-10-01") @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(example = "2026-10-31") @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResult.of("Dashboard summary", reports.summary(reports.period(from, to))));
    }

    @Operation(summary = "Sales per day", description = "Every day of the period, also days without sales.")
    @GetMapping("/sales/daily")
    public ResponseEntity<ApiResult<List<DailySales>>> daily(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResult.of("Daily sales", reports.daily(reports.period(from, to))));
    }

    @Operation(summary = "Sales per day as CSV", description = "The same rows as a CSV file, for a spreadsheet.")
    @GetMapping(value = "/sales/daily.csv", produces = "text/csv")
    public ResponseEntity<String> dailyCsv(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportPeriod period = reports.period(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sales-" + period.from() + "-to-" + period.to() + ".csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(reports.dailyCsv(period));
    }

    @Operation(summary = "Best sellers", description = "From paid orders that weren't cancelled. by = revenue (default) or units.")
    @GetMapping("/products/top")
    public ResponseEntity<ApiResult<List<TopProduct>>> topProducts(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "by", required = false) String by,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResult.of("Best sellers", reports.topProducts(reports.period(from, to), by, limit)));
    }

    @Operation(summary = "Order pipeline", description = "Orders per status right now; for waiting statuses, since when the oldest waits.")
    @GetMapping("/orders/pipeline")
    public ResponseEntity<ApiResult<List<PipelineStage>>> pipeline() {
        return ResponseEntity.ok(ApiResult.of("Order pipeline", reports.pipeline()));
    }

    @Operation(summary = "Low stock", description = "Products at or below their low-stock threshold, emptiest first.")
    @GetMapping("/inventory/low-stock")
    public ResponseEntity<ApiResult<List<LowStockItem>>> lowStock(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResult.of("Low stock", reports.lowStock(limit)));
    }

    @Operation(summary = "Activity feed", description = "Newest first: payments, failed payments, cancellations, refunds, "
            + "shipments, deliveries, missed deliveries, low stock. severity = INFO or WARNING.")
    @GetMapping("/activity")
    public ResponseEntity<ApiResult<PageResponse<ActivityItem>>> activity(
            @RequestParam(name = "severity", required = false) String severity,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResult.of("Activity", reports.activity(severity, page, size)));
    }
}
