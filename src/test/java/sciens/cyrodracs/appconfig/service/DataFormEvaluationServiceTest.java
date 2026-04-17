package sciens.cyrodracs.appconfig.service;

import org.junit.jupiter.api.Test;
import sciens.cyrodracs.appconfig.DataForm;
import sciens.cyrodracs.appconfig.DataFormElement;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataFormEvaluationServiceTest {

    private final DataFormEvaluationService service =
            new DataFormEvaluationService(null, null, null, null, null);

    @Test
    void twoLevelChain() {
        DataForm form = new DataForm();
        form.getElements().put("producer", element("producer"));
        form.getElements().put("mount", element("mount", "producer"));

        List<DataFormElement> affected = service.resolveAffectedElements(form, "producer");

        assertEquals(1, affected.size());
        assertEquals("mount", affected.get(0).getCode());
    }

    @Test
    void threeLevelChain() {
        DataForm form = new DataForm();
        form.getElements().put("producer", element("producer"));
        form.getElements().put("mount", element("mount", "producer"));
        form.getElements().put("lenses", element("lenses", "mount"));

        List<DataFormElement> affected = service.resolveAffectedElements(form, "producer");

        assertEquals(2, affected.size());
        assertEquals("mount", affected.get(0).getCode());
        assertEquals("lenses", affected.get(1).getCode());
    }

    @Test
    void diamondDependency() {
        // producer → mount, producer → sensor, mount + sensor → lens
        DataForm form = new DataForm();
        form.getElements().put("producer", element("producer"));
        form.getElements().put("mount", element("mount", "producer"));
        form.getElements().put("sensor", element("sensor", "producer"));
        form.getElements().put("lens", element("lens", "mount", "sensor"));

        List<DataFormElement> affected = service.resolveAffectedElements(form, "producer");

        assertEquals(3, affected.size());
        // lens appears only once, after both mount and sensor
        List<String> codes = affected.stream().map(DataFormElement::getCode).toList();
        assertTrue(codes.indexOf("mount") < codes.indexOf("lens"));
        assertTrue(codes.indexOf("sensor") < codes.indexOf("lens"));
    }

    @Test
    void noAffectedElements() {
        DataForm form = new DataForm();
        form.getElements().put("name", element("name"));
        form.getElements().put("producer", element("producer"));

        List<DataFormElement> affected = service.resolveAffectedElements(form, "name");

        assertTrue(affected.isEmpty());
    }

    @Test
    void changeInMiddleOfChain() {
        DataForm form = new DataForm();
        form.getElements().put("producer", element("producer"));
        form.getElements().put("mount", element("mount", "producer"));
        form.getElements().put("lenses", element("lenses", "mount"));

        List<DataFormElement> affected = service.resolveAffectedElements(form, "mount");

        assertEquals(1, affected.size());
        assertEquals("lenses", affected.get(0).getCode());
    }

    private DataFormElement element(String code, String... reloadOnChangeOf) {
        DataFormElement el = new DataFormElement();
        el.setCode(code);
        el.setReloadOnChangeOf(List.of(reloadOnChangeOf));
        return el;
    }
}
