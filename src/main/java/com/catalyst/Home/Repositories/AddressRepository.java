package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.FarmersAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AddressRepository extends JpaRepository<FarmersAddress, Long> {
}
