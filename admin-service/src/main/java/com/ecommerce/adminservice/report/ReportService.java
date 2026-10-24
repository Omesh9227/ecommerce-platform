package com.ecommerce.adminservice.report;

import com.ecommerce.adminservice.config.DashboardProperties;
import com.ecommerce.adminservice.dto.response.ActivityItem;
import com.ecommerce.adminservice.dto.response.DailySales;
import com.ecommerce.adminservice.dto.response.DashboardSummary;
import com.ecommerce.adminservice.dto.response.LowStockItem;
import com.ecommerce.adminservice.dto.response.PageResponse;
import com.ecommerce.adminservice.dto.response.PipelineStage;
import com.ecommerce.adminservice.dto.response.TopProduct;
import com.ecommerce.adminservice.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.ecommerce.adminservice.report.ReportRepository.n;

/** The dashboard's reports. All read-only; numbers come from the event copy in this service's database. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    static final int MAX_LIST = 100;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ReportRepository repository;
    private final DashboardProperties properties;
    private final Clock clock;

    public ReportPeriod period(LocalDate from, LocalDate to) {
        return ReportPeriod.of(from, to, LocalDate.ofInstant(clock.instant(), properties.zone()), properties.zone(), properties.maxRangeDays());
    }

    public DashboardSummary summary(ReportPeriod period) {
        ReportRepository.SalesTotals sales = repository.sales(period);
        ReportRepository.SalesTotals previous = repository.sales(period.previous(properties.zone()));
        BigDecimal net = sales.gross().subtract(sales.refunds());
        BigDecimal previousNet = previous.gross().subtract(previous.refunds());
        BigDecimal change = previousNet.signum() <= 0 ? null
                : net.subtract(previousNet).multiply(HUNDRED).divide(previousNet, 1, RoundingMode.HALF_UP);
        BigDecimal aov = sales.paidOrders() == 0 ? null
                : sales.gross().divide(BigDecimal.valueOf(sales.paidOrders()), 2, RoundingMode.HALF_UP);

        Map<String, Object> orders = repository.orderCounts(period);
        Map<String, Object> payments = repository.paymentCounts(period);
        long succeeded = n(payments, "succeeded");
        long failed = n(payments, "failed");
        BigDecimal rate = succeeded + failed == 0 ? null
                : BigDecimal.valueOf(succeeded).multiply(HUNDRED).divide(BigDecimal.valueOf(succeeded + failed), 1, RoundingMode.HALF_UP);
        Map<String, Object> buyers = repository.buyers(period);
        Map<String, Object> work = repository.workload(clock.instant().minus(Duration.ofDays(7)));
        Timestamp oldest = (Timestamp) work.get("oldest_to_ship");

        return new DashboardSummary(period.from(), period.to(), properties.currency(),
                new DashboardSummary.Sales(money(sales.gross()), money(sales.refunds()), money(net), sales.paidOrders(), aov,
                        sales.unitsSold(), money(previousNet), change),
                new DashboardSummary.Orders(n(orders, "placed"), n(orders, "paid"), n(orders, "cancelled_unpaid"),
                        n(orders, "cancelled_paid"), n(orders, "shipped"), n(orders, "delivered")),
                new DashboardSummary.Payments(succeeded, failed, rate, sales.refundCount()),
                new DashboardSummary.Customers(n(buyers, "buyers"), n(buyers, "new_buyers")),
                new DashboardSummary.Workload(n(work, "to_ship"), oldest == null ? null : oldest.toInstant(),
                        n(work, "in_transit"), n(work, "low_stock"), n(work, "failures")));
    }

    public List<DailySales> daily(ReportPeriod period) {
        return repository.daily(period, properties.zone());
    }

    /** The daily report as CSV (for spreadsheets). */
    public String dailyCsv(ReportPeriod period) {
        StringBuilder csv = new StringBuilder("date,paid_orders,gross_sales,refunds,net_sales,units_sold,currency\n");
        for (DailySales d : daily(period)) {
            csv.append(d.date()).append(',').append(d.paidOrders()).append(',').append(plain(d.grossSales())).append(',')
                    .append(plain(d.refunds())).append(',').append(plain(d.netSales())).append(',').append(d.unitsSold())
                    .append(',').append(properties.currency()).append('\n');
        }
        return csv.toString();
    }

    public List<TopProduct> topProducts(ReportPeriod period, String by, int limit) {
        checkLimit(limit);
        String sort = by == null ? "revenue" : by.trim().toLowerCase(Locale.ROOT);
        if (!sort.equals("revenue") && !sort.equals("units")) {
            throw new BadRequestException("by must be revenue or units");
        }
        return repository.topProducts(period, sort.equals("units"), limit);
    }

    public List<PipelineStage> pipeline() {
        return repository.pipeline();
    }

    public List<LowStockItem> lowStock(int limit) {
        checkLimit(limit);
        return repository.lowStock(limit);
    }

    public PageResponse<ActivityItem> activity(String severity, int page, int size) {
        String level = null;
        if (severity != null && !severity.isBlank()) {
            level = severity.trim().toUpperCase(Locale.ROOT);
            if (!level.equals("INFO") && !level.equals("WARNING")) {
                throw new BadRequestException("severity must be INFO or WARNING");
            }
        }
        if (page < 0 || size < 1 || size > MAX_LIST) {
            throw new BadRequestException("page must be 0 or more and size between 1 and " + MAX_LIST);
        }
        return PageResponse.of(repository.activity(level, page, size), page, size, repository.activityCount(level));
    }

    private static void checkLimit(int limit) {
        if (limit < 1 || limit > MAX_LIST) {
            throw new BadRequestException("limit must be between 1 and " + MAX_LIST);
        }
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
