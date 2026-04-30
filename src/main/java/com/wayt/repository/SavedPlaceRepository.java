package com.wayt.repository;

import com.wayt.domain.SavedPlace;
import com.wayt.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedPlaceRepository extends JpaRepository<SavedPlace, UUID> {
    List<SavedPlace> findByOwner(UserAccount owner);

    Optional<SavedPlace> findByOwnerAndPlaceNameAndLatitudeAndLongitude(
            UserAccount owner,
            String placeName,
            double latitude,
            double longitude
    );

    Optional<SavedPlace> findByIdAndOwner(UUID id, UserAccount owner);
}
