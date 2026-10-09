package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.MarketplaceListing;
import com.catalyst.Home.Utils.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarketplaceListingRepository extends JpaRepository<MarketplaceListing, Long> {
    Optional<MarketplaceListing> findByUuid(String uuid);
    List<MarketplaceListing> findBySellerFarmer_Uuid(String farmerUuid);
    List<MarketplaceListing> findByStatus(ListingStatus status);
}
