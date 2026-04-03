package sciens.cyrodracs.appconfig.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppConfigTypeRepository extends JpaRepository<AppConfigTypeEntity, Long> {

    Optional<AppConfigTypeEntity> findByCode(String code);

    boolean existsByCode(String code);
}
