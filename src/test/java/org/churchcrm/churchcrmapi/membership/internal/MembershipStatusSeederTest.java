package org.churchcrm.churchcrmapi.membership.internal;

import org.churchcrm.churchcrmapi.organization.ChurchCreated;
import org.churchcrm.churchcrmapi.utils.TestAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ApplicationModuleTest
@Import(TestAuditingConfig.class)
class MembershipStatusSeederTest {

    @Autowired
    private MembershipStatusSeeder seeder;

    @Autowired
    private MembershipStatusRepository repository;

    @Test
    void seedsSixDefaultsWhenChurchCreated(Scenario scenario) {
        UUID churchId = UUID.randomUUID();
        var event = churchCreated(churchId);

        scenario.publish(event)
                .andWaitForStateChange(() -> {
                    var statuses = repository.findByChurchIdOrderBySortOrderAsc(churchId);
                    return statuses.size() == 6 ? statuses : null;
                })
                .andVerify(statuses -> {
                    assertThat(statuses).hasSize(6);
                    assertThat(statuses).extracting(MembershipStatus::getCode)
                            .containsExactly("member", "regular_attender", "guest", "non_attender", "excluded", "disciplined");
                    assertThat(statuses).extracting(MembershipStatus::getSortOrder)
                            .containsExactly(10, 20, 30, 40, 50, 60);
                    assertThat(statuses).filteredOn(MembershipStatus::getIsDefault)
                            .extracting(MembershipStatus::getCode)
                            .containsExactly("guest");
                    assertThat(statuses).allSatisfy(status -> {
                        assertThat(status.getAudit().getCreatedBy()).isEqualTo("system");
                        assertThat(status.getAudit().getUpdatedBy()).isEqualTo("system");
                        assertThat(status.getAudit().getCreatedAt()).isNotNull();
                        assertThat(status.getAudit().getUpdatedAt()).isNotNull();
                    });
                });
    }

    @Test
    @Transactional
    void duplicateDeliveryIsIdempotent() {
        UUID churchId = UUID.randomUUID();
        var event = churchCreated(churchId);

        seeder.seed(churchId);
        assertThat(repository.findByChurchIdOrderBySortOrderAsc(churchId)).hasSize(6);

        assertThatCode(() -> seeder.seed(churchId)).doesNotThrowAnyException();
        assertThat(repository.findByChurchIdOrderBySortOrderAsc(churchId)).hasSize(6);
    }

    @Test
    @Transactional
    void customDefaultBeforeSeedingLeavesOneDefault() {
        UUID churchId = UUID.randomUUID();
        var customDefault = new MembershipStatus();
        customDefault.setChurchId(churchId);
        customDefault.setCode("custom_default");
        customDefault.setLabel("Custom Default");
        customDefault.setSortOrder(0);
        customDefault.setIsDefault(true);
        customDefault.setActive(true);
        repository.saveAndFlush(customDefault);

        assertThatCode(() -> seeder.seed(churchId)).doesNotThrowAnyException();

        var statuses = repository.findByChurchIdOrderBySortOrderAsc(churchId);
        assertThat(statuses).hasSize(6);
        assertThat(statuses).filteredOn(MembershipStatus::getIsDefault)
                .extracting(MembershipStatus::getCode)
                .containsExactly("custom_default");
    }

    private ChurchCreated churchCreated(UUID churchId) {
        return new ChurchCreated(churchId, "Test Church", "admin", "admin@test.com", "Admin User");
    }
}
