package com.epms.repository;

import com.epms.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findAllByOrderByExpenseDateDescExpenseIdDesc();

    List<Expense> findByStatusOrderByExpenseDateDescExpenseIdDesc(String status);

    List<Expense> findByStatusInAndExpenseDateBetween(Collection<String> statuses, LocalDate from, LocalDate to);

    long countByStatus(String status);
}
