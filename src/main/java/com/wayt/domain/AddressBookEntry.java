package com.wayt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "address_book_entries")
public class AddressBookEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserAccount owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserAccount savedUser;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected AddressBookEntry() {
    }

    public AddressBookEntry(UserAccount owner, UserAccount savedUser, String displayName) {
        this.owner = owner;
        this.savedUser = savedUser;
        this.displayName = displayName;
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getOwner() {
        return owner;
    }

    public UserAccount getSavedUser() {
        return savedUser;
    }

    public String getDisplayName() {
        return displayName;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
