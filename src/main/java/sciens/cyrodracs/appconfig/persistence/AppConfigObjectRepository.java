package sciens.cyrodracs.appconfig.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppConfigObjectRepository extends JpaRepository<AppConfigObjectEntity, Long> {

    List<AppConfigObjectEntity> findByParentObject(AppConfigObjectEntity parent);
}
