package com.wayt.service;

import com.wayt.domain.AuthProvider;
import com.wayt.domain.UserAccount;
import com.wayt.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "wayt.app.public-base-url=https://current.example",
        "spring.datasource.url=jdbc:h2:mem:response-mapper-avatar-url;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class ResponseMapperAvatarUrlTests {
    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private ResponseMapper responseMapper;

    @Test
    @Transactional
    void userResponseRebasesUploadedAvatarUrlsToCurrentPublicBaseUrl() {
        String suffix = UUID.randomUUID().toString();
        UserAccount user = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "avatar-rebase-" + suffix,
                "@avatar_rebase_" + suffix.substring(0, 8),
                "avatar-user",
                "http://old.example/api/uploads/avatars/avatar.jpg"
        ));

        var response = responseMapper.user(user);

        assertThat(response.avatarUrl()).isEqualTo("https://current.example/api/uploads/avatars/avatar.jpg");
    }

    @Test
    @Transactional
    void userResponseKeepsExternalAvatarUrlsUnchanged() {
        String suffix = UUID.randomUUID().toString();
        UserAccount user = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "external-avatar-" + suffix,
                "@external_avatar_" + suffix.substring(0, 8),
                "external-avatar-user",
                "https://cdn.example/avatar.jpg"
        ));

        var response = responseMapper.user(user);

        assertThat(response.avatarUrl()).isEqualTo("https://cdn.example/avatar.jpg");
    }
}
