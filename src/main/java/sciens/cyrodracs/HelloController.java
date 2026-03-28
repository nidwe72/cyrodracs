package sciens.cyrodracs;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class HelloController {

    private final Random random = new Random();

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        int number = random.nextInt(10000);
        return Map.of(
                "message", "Hello World",
                "number", number
        );
    }

    private static final String[] PARENT_WORDS = {
        "apple", "banana", "cherry", "grape", "mango",
        "orange", "peach", "pear", "plum", "strawberry",
        "book", "card", "chair", "door", "house",
        "lamp", "table", "window", "arrow", "button",
        "cable", "diamond", "engine", "filter", "garden",
        "hammer", "island", "jacket", "kettle", "ladder",
        "mirror", "needle", "pillow", "rocket", "socket",
        "tunnel", "umbrella", "valley", "wallet", "zipper"
    };

    private static final String[] CHILD_WORDS = {
        "alpha", "beta", "gamma", "delta", "epsilon"
    };

    @GetMapping("/demo-form")
    public DataForm demoForm() {
        return new DataForm(List.of(
            new DataFormElement("firstName",  "First Name",         "inputString",  List.of(),                                                      6,  false, null, null, null),
            new DataFormElement("lastName",   "Last Name",          "inputString",  List.of(),                                                      6,  false, null, null, null),
            new DataFormElement("email",      "Email",              "inputEmail",   List.of(),                                                      6,  true,  null, null, null),
            new DataFormElement("password",   "Password",           "inputPassword",List.of(),                                                      6,  false, null, null, null),
            new DataFormElement("birthDate",  "Birth Date",         "datePicker",   List.of(),                                                      6,  true,  null, null, null),
            new DataFormElement("prefTime",   "Preferred Time",     "timePicker",   List.of(),                                                      6,  false, null, null, null),
            new DataFormElement("appointment","Appointment",        "dateTimePicker",List.of(),                                                     6,  true,  null, null, null),
            new DataFormElement("vacation",   "Vacation",           "dateRangePicker",List.of(),                                                    6,  false, null, null, null),
            new DataFormElement("country",    "Country",            "select",       List.of("Austria","France","Germany","Italy","Switzerland"),     6,  true,  null, null, null),
            new DataFormElement("languages",  "Languages",          "multiSelect",  List.of("English","German","French","Spanish","Italian"),        6,  false, null, null, null),
            new DataFormElement("age",        "Age",                "inputNumber",  List.of(),                                                      6,  true,  null, null, null),
            new DataFormElement("satisfaction","Satisfaction",      "rating",       List.of(),                                                      6,  false, null, null, null),
            new DataFormElement("newsletter", "Newsletter",         "checkbox",     List.of(),                                                      6,  true,  null, null, null),
            new DataFormElement("active",     "Active",             "toggle",       List.of(),                                                      6,  false, null, null, null),
            new DataFormElement("experience", "Experience (years)", "slider",       List.of(),                                                      12, true,  0.0,  30.0, null),
            new DataFormElement("salutation", "Salutation",         "radioGroup",   List.of("Mr","Ms","Dr"),                                        6,  true,  null, null, null),
            new DataFormElement("interests",  "Interests",          "checkboxGroup",List.of("Sports","Music","Travel","Technology"),                 6,  false, null, null, null),
            new DataFormElement("notes",      "Notes",              "textarea",     List.of(),                                                      12, true,  null, null, 5)
        ));
    }

    @GetMapping("/tree")
    public TestNode tree() {
        List<TestNode> children = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String parentWord = PARENT_WORDS[i % PARENT_WORDS.length];
            List<TestNode> grandchildren = new ArrayList<>();
            for (int j = 0; j < 5; j++) {
                grandchildren.add(new TestNode("%s-%d".formatted(CHILD_WORDS[j], i), List.of()));
            }
            children.add(new TestNode("%s-%d".formatted(parentWord, i), grandchildren));
        }
        return new TestNode("root", children);
    }
}
