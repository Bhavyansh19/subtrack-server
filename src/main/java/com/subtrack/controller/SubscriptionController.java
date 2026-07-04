package com.subtrack.controller;

import com.subtrack.model.Subscription;
import com.subtrack.service.SubscriptionService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
@CrossOrigin(origins = "${CLIENT_URL:http://localhost:5173}")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @GetMapping
    public List<Subscription> getAll() {
        return service.findAll();
    }

    @PostMapping
    public Subscription create(@RequestBody Subscription sub) {
        return service.create(sub);
    }

    @PutMapping("/{id}")
    public Subscription update(@PathVariable Long id, @RequestBody Subscription sub) {
        return service.update(id, sub);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/renew")
    public Subscription renew(@PathVariable Long id) {
        return service.renewNow(id);
    }

    @GetMapping("/renewing-soon")
    public List<Subscription> renewingSoon(@RequestParam(defaultValue = "7") int days) {
        return service.findRenewingWithinDays(days);
    }

    @GetMapping("/analytics/total-monthly")
    public Map<String, BigDecimal> totalMonthly() {
        return Map.of("totalMonthlySpend", service.totalMonthlySpend());
    }

    @GetMapping("/analytics/by-category")
    public Map<String, BigDecimal> byCategory() {
        return service.monthlySpendByCategory();
    }
}
