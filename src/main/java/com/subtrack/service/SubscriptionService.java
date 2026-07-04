package com.subtrack.service;

import com.subtrack.model.Subscription;
import com.subtrack.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SubscriptionService {

    private final SubscriptionRepository repository;

    public SubscriptionService(SubscriptionRepository repository) {
        this.repository = repository;
    }

    public Subscription create(Subscription sub) {
        sub.setNextRenewalDate(computeNextRenewal(sub.getStartDate(), sub.getBillingCycle()));
        return repository.save(sub);
    }

    public List<Subscription> findAll() {
        return repository.findByActiveTrue();
    }

    public Subscription update(Long id, Subscription updated) {
        Subscription existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + id));

        existing.setName(updated.getName());
        existing.setCategory(updated.getCategory());
        existing.setCost(updated.getCost());
        existing.setBillingCycle(updated.getBillingCycle());
        existing.setStartDate(updated.getStartDate());
        // recompute renewal date since cycle/start date may have changed
        existing.setNextRenewalDate(computeNextRenewal(updated.getStartDate(), updated.getBillingCycle()));

        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    /**
     * Marks a subscription as renewed today, advancing nextRenewalDate to
     * the following cycle. This is the "roll forward" action — call it
     * when a renewal actually happens (or via a scheduled job, not built
     * here, but this method is exactly what that job would call).
     */
    public Subscription renewNow(Long id) {
        Subscription sub = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + id));
        sub.setNextRenewalDate(computeNextRenewal(sub.getNextRenewalDate(), sub.getBillingCycle()));
        return repository.save(sub);
    }

    public List<Subscription> findRenewingWithinDays(int days) {
        LocalDate today = LocalDate.now();
        return repository.findRenewingBetween(today, today.plusDays(days));
    }

    /**
     * The real logic worth explaining in an interview: normalizing every
     * subscription's cost to a common "per month" basis regardless of its
     * actual billing cycle, so a $120/year subscription and a $10/month
     * subscription can be summed meaningfully in one number.
     */
    public BigDecimal totalMonthlySpend() {
        return repository.findByActiveTrue().stream()
                .map(this::normalizeToMonthly)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public Map<String, BigDecimal> monthlySpendByCategory() {
        return repository.findByActiveTrue().stream()
                .collect(Collectors.groupingBy(
                        s -> s.getCategory() == null ? "Uncategorized" : s.getCategory(),
                        Collectors.reducing(BigDecimal.ZERO, this::normalizeToMonthly, BigDecimal::add)
                ));
    }

    private BigDecimal normalizeToMonthly(Subscription sub) {
        return switch (sub.getBillingCycle()) {
            case WEEKLY -> sub.getCost().multiply(BigDecimal.valueOf(52)).divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
            case MONTHLY -> sub.getCost();
            case YEARLY -> sub.getCost().divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
        };
    }

    /**
     * The genuinely tricky part of "recurring date" logic: adding a month
     * (or year) to a date doesn't always land on a valid day. A subscription
     * that started Jan 31 has no "Feb 31" — real billing systems (and this
     * one) clamp to the last valid day of the target month instead of
     * silently rolling over into March, which is what naive date-add does.
     *
     * Example: Jan 31 + 1 month -> Feb 28 (or 29 in a leap year), not Mar 3.
     */
    private LocalDate computeNextRenewal(LocalDate from, Subscription.BillingCycle cycle) {
        return switch (cycle) {
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> addMonthsClamped(from, 1);
            case YEARLY -> addMonthsClamped(from, 12);
        };
    }

    private LocalDate addMonthsClamped(LocalDate from, int monthsToAdd) {
        YearMonth targetMonth = YearMonth.from(from).plusMonths(monthsToAdd);
        int clampedDay = Math.min(from.getDayOfMonth(), targetMonth.lengthOfMonth());
        return targetMonth.atDay(clampedDay);
    }
}
