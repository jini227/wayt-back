package com.wayt.repository;

import com.wayt.domain.AddressBookEntry;
import com.wayt.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressBookEntryRepository extends JpaRepository<AddressBookEntry, UUID> {
    List<AddressBookEntry> findByOwnerOrderByDisplayNameAsc(UserAccount owner);

    Optional<AddressBookEntry> findByOwnerAndSavedUser(UserAccount owner, UserAccount savedUser);

    Optional<AddressBookEntry> findByIdAndOwner(UUID id, UserAccount owner);
}
