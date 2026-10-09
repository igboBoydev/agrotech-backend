package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.LoanRepayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, Long> {
}
