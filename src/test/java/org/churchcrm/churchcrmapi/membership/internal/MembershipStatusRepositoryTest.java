package org.churchcrm.churchcrmapi.membership.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class MembershipStatusRepositoryTest {

    @Autowired
    private MembershipStatusRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void uniqueIndexRejectsDuplicateCodeCaseInsensitively() {
        UUID churchId = UUID.randomUUID();
        repository.saveAndFlush(status(churchId, "member", false));

        assertThatThrownBy(() -> repository.saveAndFlush(status(churchId, "MEMBER", false)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameCodeIsAllowedInAnotherChurch() {
        UUID churchX = UUID.randomUUID();
        UUID churchY = UUID.randomUUID();
        repository.saveAndFlush(status(churchX, "member", false));

        assertThatCode(() -> repository.saveAndFlush(status(churchY, "member", false)))
                .doesNotThrowAnyException();
    }

    @Test
    void partialUniqueIndexRejectsSecondDefault() {
        UUID churchId = UUID.randomUUID();
        repository.saveAndFlush(status(churchId, "guest", true));

        assertThatThrownBy(() -> repository.saveAndFlush(status(churchId, "custom_default", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void churchIdHasNoCrossSchemaForeignKey() {
        Integer foreignKeyCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.constraint_schema = kcu.constraint_schema
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND tc.table_schema = 'membership'
                  AND tc.table_name = 'membership_statuses'
                  AND kcu.column_name = 'church_id'
                """, Integer.class);

        assertThat(foreignKeyCount).isZero();
    }

    private MembershipStatus status(UUID churchId, String code, boolean isDefault) {
        var status = new MembershipStatus();
        status.setChurchId(churchId);
        status.setCode(code);
        status.setLabel(code);
        status.setSortOrder(0);
        status.setIsDefault(isDefault);
        status.setActive(true);
        return status;
    }
}
