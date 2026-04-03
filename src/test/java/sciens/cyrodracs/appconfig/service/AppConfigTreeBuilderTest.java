package sciens.cyrodracs.appconfig.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import sciens.cyrodracs.appconfig.AppConfig;
import sciens.cyrodracs.appconfig.DataFormElementType;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectEntity;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectRepository;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppConfigTreeBuilderTest {

    @Mock
    AppConfigObjectRepository objectRepo;

    @InjectMocks
    AppConfigTreeBuilder builder;

    // Type entities
    AppConfigTypeEntity appConfigType;
    AppConfigTypeEntity dataFormType;
    AppConfigTypeEntity dataFormElementType;
    AppConfigTypeEntity dataFormElementEnumType;

    @BeforeEach
    void setUpTypes() {
        appConfigType         = typeEntity(1L, "AppConfig");
        dataFormType          = typeEntity(2L, "DataForm");
        dataFormElementType   = typeEntity(3L, "DataFormElement");
        dataFormElementEnumType = typeEntity(4L, "DataFormElementType");
    }

    @Test
    void returnsNullWhenNoObjectsExist() {
        when(objectRepo.findAll()).thenReturn(List.of());
        assertNull(builder.buildTree());
    }

    @Test
    void returnsRootWithNoDataFormsWhenOnlyRootExists() {
        AppConfigObjectEntity root = objectEntity(1L, appConfigType, "default", null);
        when(objectRepo.findAll()).thenReturn(List.of(root));

        AppConfig config = builder.buildTree();

        assertNotNull(config);
        assertEquals(1L, config.getId());
        assertEquals("default", config.getCode());
        assertTrue(config.getDataForms().isEmpty());
    }

    @Test
    void buildsFullTreeWithDataFormsAndElements() {
        // Root
        AppConfigObjectEntity root = objectEntity(1L, appConfigType, "default", null);

        // DataForms
        AppConfigObjectEntity form1 = objectEntity(2L, dataFormType, "userRegistration", root);
        AppConfigObjectEntity form2 = objectEntity(3L, dataFormType, "productSearch", root);

        // Elements under form1
        AppConfigObjectEntity elem1 = objectEntity(4L, dataFormElementType, "firstName", form1);
        AppConfigObjectEntity elem1Type = enumTypeEntity(5L, dataFormElementEnumType, "firstName_type", elem1, "INPUT_STRING");
        AppConfigObjectEntity elem2 = objectEntity(6L, dataFormElementType, "lastName", form1);
        AppConfigObjectEntity elem2Type = enumTypeEntity(7L, dataFormElementEnumType, "lastName_type", elem2, "INPUT_STRING");

        // Elements under form2
        AppConfigObjectEntity elem3 = objectEntity(8L, dataFormElementType, "keyword", form2);
        AppConfigObjectEntity elem3Type = enumTypeEntity(9L, dataFormElementEnumType, "keyword_type", elem3, "INPUT_STRING");

        when(objectRepo.findAll()).thenReturn(
                List.of(root, form1, form2, elem1, elem1Type, elem2, elem2Type, elem3, elem3Type));

        AppConfig config = builder.buildTree();

        assertNotNull(config);
        assertEquals("default", config.getCode());
        assertEquals(2, config.getDataForms().size());

        var userReg = config.getDataForms().get("userRegistration");
        assertNotNull(userReg);
        assertEquals(2L, userReg.getId());
        assertEquals(2, userReg.getElements().size());

        var firstName = userReg.getElements().get("firstName");
        assertNotNull(firstName);
        assertEquals(4L, firstName.getId());
        assertEquals(DataFormElementType.INPUT_STRING, firstName.getType());

        var lastName = userReg.getElements().get("lastName");
        assertNotNull(lastName);
        assertEquals(DataFormElementType.INPUT_STRING, lastName.getType());

        var productSearch = config.getDataForms().get("productSearch");
        assertNotNull(productSearch);
        assertEquals(1, productSearch.getElements().size());
        assertNotNull(productSearch.getElements().get("keyword"));
    }

    @Test
    void elementWithNoEnumChildHasNullType() {
        AppConfigObjectEntity root = objectEntity(1L, appConfigType, "default", null);
        AppConfigObjectEntity form = objectEntity(2L, dataFormType, "myForm", root);
        AppConfigObjectEntity elem = objectEntity(3L, dataFormElementType, "field1", form);
        // No DataFormElementType child added

        when(objectRepo.findAll()).thenReturn(List.of(root, form, elem));

        AppConfig config = builder.buildTree();
        assertNull(config.getDataForms().get("myForm").getElements().get("field1").getType());
    }

    // ---- helpers ----

    private AppConfigTypeEntity typeEntity(Long id, String code) {
        AppConfigTypeEntity t = new AppConfigTypeEntity();
        ReflectionTestUtils.setField(t, "id", id);
        t.setCode(code);
        return t;
    }

    private AppConfigObjectEntity objectEntity(Long id, AppConfigTypeEntity type,
                                               String code, AppConfigObjectEntity parent) {
        AppConfigObjectEntity e = new AppConfigObjectEntity();
        ReflectionTestUtils.setField(e, "id", id);
        e.setType(type);
        e.setCode(code);
        e.setParentObject(parent);
        return e;
    }

    private AppConfigObjectEntity enumTypeEntity(Long id, AppConfigTypeEntity type,
                                                  String code, AppConfigObjectEntity parent,
                                                  String enumValue) {
        AppConfigObjectEntity e = objectEntity(id, type, code, parent);
        e.setEnumValue(enumValue);
        return e;
    }
}
