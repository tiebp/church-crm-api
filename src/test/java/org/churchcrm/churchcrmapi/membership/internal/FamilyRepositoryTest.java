package org.churchcrm.churchcrmapi.membership.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class FamilyRepositoryTest {

    @Autowired
    private FamilyRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void uniqueIndexRejectsDuplicateNameCaseInsensitively() {
        UUID churchId = UUID.randomUUID();
        repository.saveAndFlush(family(churchId, "Doe"));

        assertThatThrownBy(() -> repository.saveAndFlush(family(churchId, "doe")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameNameIsAllowedInAnotherChurch() {
        UUID churchX = UUID.randomUUID();
        UUID churchY = UUID.randomUUID();
        repository.saveAndFlush(family(churchX, "Doe"));

        assertThatCode(() -> repository.saveAndFlush(family(churchY, "Doe")))
                .doesNotThrowAnyException();
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
                  AND tc.table_name = 'families'
                  AND kcu.column_name = 'church_id'
                """, Integer.class);

        assertThat(foreignKeyCount).isZero();
    }

    @Test
    void migrationCreatesExpectedColumnsAndIndexes() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'membership'
                  AND table_name = 'families'
                """, String.class);

        assertThat(columns).contains(
                "id", "church_id", "name", "line1", "line2", "city", "state", "country",
                "postal_code", "wedding_date", "created_at", "updated_at", "created_by", "updated_by");

        Integer indexCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname = 'membership'
                  AND tablename = 'families'
                  AND indexname IN ('idx_families_church_id', 'idx_families_unique_name_per_church')
                """, Integer.class);

        assertThat(indexCount).isEqualTo(2);
    }

    private Family family(UUID churchId, String name) {
        var family = new Family();
        family.setChurchId(churchId);
        family.setName(name);
        return family;
    }
}
