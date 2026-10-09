package com.nevis.search;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class ApiIntegrationTest extends IntegrationTest {

    private ResultActions search(String q) throws Exception {
        return mvc.perform(get("/search").param("q", q)).andExpect(status().isOk());
    }

    @Nested
    class Clients {

        @Test
        void createsClientAndReturnsIt() throws Exception {
            String body = postJson("/clients", """
                    {"first_name": "John", "last_name": "Doe", "email": "John.Doe@NevisWealth.com",
                     "description": "Retired teacher", "social_links": ["https://linkedin.com/in/johndoe"]}""")
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString("/clients/")))
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.first_name").value("John"))
                    .andExpect(jsonPath("$.last_name").value("Doe"))
                    .andExpect(jsonPath("$.email").value("john.doe@neviswealth.com"))
                    .andExpect(jsonPath("$.description").value("Retired teacher"))
                    .andExpect(jsonPath("$.social_links[0]").value("https://linkedin.com/in/johndoe"))
                    .andReturn().getResponse().getContentAsString();

            mvc.perform(get("/clients/" + JsonPath.read(body, "$.id")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("john.doe@neviswealth.com"));
        }

        @Test
        void rejectsDuplicateEmailIgnoringCase() throws Exception {
            createClient("John", "Doe", "john@example.com", "");
            postJson("/clients", """
                    {"first_name": "Johnny", "last_name": "Doe", "email": "JOHN@example.com"}""")
                    .andExpect(status().isConflict());
        }

        @Test
        void listsAllClientsSortedByName() throws Exception {
            mvc.perform(get("/clients")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

            createClient("Zoe", "Brown", "zoe@example.com", "");
            createClient("Adam", "Smith", "adam@example.com", "");
            createClient("Anna", "Brown", "anna@example.com", "");

            mvc.perform(get("/clients"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].email",
                            contains("anna@example.com", "zoe@example.com", "adam@example.com")));
        }

        @Test
        void unknownClientIsNotFound() throws Exception {
            mvc.perform(get("/clients/2f1b6d3e-0000-4000-8000-000000000000")).andExpect(status().isNotFound());
            mvc.perform(get("/clients/not-a-uuid")).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Documents {

        @Test
        void createsDocumentForClient() throws Exception {
            String clientId = createClient("John", "Doe", "john@example.com", "");
            String body = postJson("/clients/" + clientId + "/documents", """
                    {"title": "Utility bill", "content": "Electricity bill for March."}""")
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString("/documents/")))
                    .andExpect(jsonPath("$.client_id").value(clientId))
                    .andExpect(jsonPath("$.title").value("Utility bill"))
                    .andExpect(jsonPath("$.content").value("Electricity bill for March."))
                    .andExpect(jsonPath("$.created_at").isNotEmpty())
                    .andExpect(jsonPath("$.summary").doesNotExist()) // no LLM configured in tests
                    .andReturn().getResponse().getContentAsString();

            mvc.perform(get("/documents/" + JsonPath.read(body, "$.id"))).andExpect(status().isOk());
        }

        @Test
        void listsOnlyTheDocumentsOfTheClientNewestFirst() throws Exception {
            String john = createClient("John", "Doe", "john@example.com", "");
            String jane = createClient("Jane", "Roe", "jane@example.com", "");
            String older = createDocument(john, "Older", "First document.");
            String newer = createDocument(john, "Newer", "Second document.");
            createDocument(jane, "Not John's", "Belongs to Jane.");

            mvc.perform(get("/clients/" + john + "/documents"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", contains(newer, older)));
            mvc.perform(get("/clients/" + createClient("Ann", "Lee", "ann@example.com", "") + "/documents"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void listingDocumentsOfUnknownClientIsNotFound() throws Exception {
            mvc.perform(get("/clients/2f1b6d3e-0000-4000-8000-000000000000/documents"))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/clients/not-a-uuid/documents")).andExpect(status().isNotFound());
        }

        @Test
        void addingDocumentToUnknownClientIsNotFound() throws Exception {
            postJson("/clients/2f1b6d3e-0000-4000-8000-000000000000/documents", """
                    {"title": "t", "content": "c"}""").andExpect(status().isNotFound());
        }

        @Test
        void unknownDocumentIsNotFound() throws Exception {
            mvc.perform(get("/documents/2f1b6d3e-0000-4000-8000-000000000000")).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Search {

        @Test
        void findsClientByEmailDomain() throws Exception {
            String john = createClient("John", "Doe", "john.doe@neviswealth.com", "");
            createClient("Jane", "Roe", "jane.roe@example.com", "");

            search("NevisWealth")
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].type").value("client"))
                    .andExpect(jsonPath("$[0].client.id").value(john))
                    .andExpect(jsonPath("$[0].client.email").value("john.doe@neviswealth.com"))
                    .andExpect(jsonPath("$[0].highlights.email[0]").value("john.doe@<em>neviswealth</em>.com"));
        }

        @Test
        void findsClientByPartOfEmailOrName() throws Exception {
            String john = createClient("John", "Doe", "john.doe@neviswealth.com", "");
            createClient("Jane", "Roe", "jane.roe@example.com", "");

            search("nevis")
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].client.id").value(john))
                    .andExpect(jsonPath("$[0].highlights.email[0]", containsString("<em>")));
            search("wealth").andExpect(jsonPath("$[*].client.id", contains(john)));
            search("ohn").andExpect(jsonPath("$[*].client.id", contains(john)));
            search("jo").andExpect(jsonPath("$", hasSize(0))); // parts shorter than 3 characters are not matched
        }

        @Test
        void wholeWordMatchRanksAbovePartialMatch() throws Exception {
            String john = createClient("John", "Doe", "jd@example.com", "");
            String johnson = createClient("Alice", "Johnson", "aj@example.com", "");

            search("john").andExpect(jsonPath("$[*].client.id", contains(john, johnson)));
        }

        @Test
        void findsClientByNameAndDescription() throws Exception {
            String john = createClient("John", "Doe", "jd@example.com", "Retired teacher interested in ESG funds");
            createClient("Jane", "Roe", "jr@example.com", "Tech entrepreneur");

            search("doe").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].client.id").value(john));
            search("JOHN").andExpect(jsonPath("$[0].client.id").value(john));
            search("teachers") // plural and singular match through stemming
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].client.id").value(john))
                    .andExpect(jsonPath("$[0].highlights.description").exists());
        }

        @Test
        void findsDocumentWithUtilityBillWhenSearchingForAddressProof() throws Exception {
            String clientId = createClient("John", "Doe", "jd@example.com", "");
            String bill = createDocument(clientId, "Scan 2024-03",
                    "Utility bill from EDF Energy for March 2024. Account holder John Doe, 12 Baker Street, London.");
            createDocument(clientId, "Meeting notes", "We talked about the holiday plans and a new sailing boat.");

            search("address proof")
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].type").value("document"))
                    .andExpect(jsonPath("$[0].document.id").value(bill))
                    .andExpect(jsonPath("$[0].highlights.content[0]", containsString("<em>Utility")));
        }

        @Test
        void relatedTermsOnlyWidenFromConceptToExamples() throws Exception {
            String clientId = createClient("John", "Doe", "jd@example.com", "");
            String bill = createDocument(clientId, "Bill", "Utility bill for March.");
            createDocument(clientId, "Letter", "Proof of address issued by the bank.");

            // a utility bill proves an address, but asking for a utility bill should not return other proofs
            search("utility bill")
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].document.id").value(bill));
        }

        @Test
        void matchesDocumentWordsRegardlessOfCaseAndPlural() throws Exception {
            String clientId = createClient("John", "Doe", "jd@example.com", "");
            String passport = createDocument(clientId, "ID", "Passport copy, number 123456789, expires 2031.");

            search("PASSPORTS").andExpect(jsonPath("$[0].document.id").value(passport));
        }

        @Test
        void returnsClientsAndDocumentsTogether() throws Exception {
            String clientId = createClient("Tax", "Payer", "tp@example.com", "");
            createDocument(clientId, "Tax return 2023", "Self assessment tax return filed with HMRC.");

            search("tax")
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[*].type", hasItem("client")))
                    .andExpect(jsonPath("$[*].type", hasItem("document")));
        }

        @Test
        void returnsEmptyArrayWhenNothingMatches() throws Exception {
            String clientId = createClient("John", "Doe", "jd@example.com", "");
            createDocument(clientId, "Utility bill", "Electricity bill for March.");

            search("zzzyqx").andExpect(jsonPath("$", hasSize(0)));
            search("johndoe").andExpect(jsonPath("$", hasSize(0))); // words are matched, not glued-together text
        }

        @Test
        void findsDocumentByMeaningWithoutSharedWords() throws Exception {
            String clientId = createClient("Maria", "Garcia", "mg@example.com", "");
            String questionnaire = createDocument(clientId, "Questionnaire",
                    "Maria accepts high volatility and could tolerate a loss of up to 25 percent in a single year.");
            createDocument(clientId, "Invoice", "Invoice for repairing the garden fence and painting the shed.");

            // no word of the query occurs in the document
            search("how much risk can she handle")
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].document.id").value(questionnaire))
                    .andExpect(jsonPath("$[0].highlights.passage[0]", containsString("tolerate a loss")))
                    .andExpect(jsonPath("$[0].highlights.content").doesNotExist());
        }

        @Test
        void retiringEarlyFindsPlansToStopWorking() throws Exception {
            String clientId = createClient("John", "Doe", "jd@example.com", "");
            String plan = createDocument(clientId, "Meeting notes",
                    "John wants to stop working at 58 and live off his savings and investments.");
            createDocument(clientId, "Invoice", "Invoice for repairing the garden fence and painting the shed.");

            search("retiring early").andExpect(jsonPath("$[*].document.id", contains(plan)));
        }

        @Test
        void unrelatedQueryDoesNotMatchByMeaning() throws Exception {
            String clientId = createClient("Maria", "Garcia", "mg@example.com", "");
            createDocument(clientId, "Questionnaire",
                    "Maria accepts high volatility and could tolerate a loss of up to 25 percent in a single year.");
            createDocument(clientId, "Bill", "Electricity and gas bill for March, amount due 84.20 GBP.");

            search("football results").andExpect(jsonPath("$", hasSize(0)));
            search("kitchen renovation").andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void wordAndMeaningMatchRanksAboveMeaningOnly() throws Exception {
            String clientId = createClient("John", "Doe", "jd@example.com", "");
            String pension = createDocument(clientId, "Pension",
                    "Annual pension statement: the pension pot is worth 410,000 pounds.");
            createDocument(clientId, "Plans",
                    "John wants to stop working at 58 and live off his savings and investments.");

            search("pension").andExpect(jsonPath("$[0].document.id").value(pension));
        }
    }

    @Nested
    class Validation {

        @Test
        void clientNeedsOnlyNamesAndEmail() throws Exception {
            postJson("/clients", """
                    {"first_name": "Ann", "last_name": "Lee", "email": "ann@example.com"}""")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.description").doesNotExist())
                    .andExpect(jsonPath("$.social_links", hasSize(0)));
        }

        @Test
        void clientRequiresNamesAndAValidEmail() throws Exception {
            postJson("/clients", """
                    {"first_name": " ", "email": "not-an-email"}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.first_name").exists())
                    .andExpect(jsonPath("$.errors.last_name").exists())
                    .andExpect(jsonPath("$.errors.email").exists());
        }

        @Test
        void clientInputIsTrimmedAndBlankDescriptionIsAbsent() throws Exception {
            postJson("/clients", """
                    {"first_name": "  John ", "last_name": " Doe ", "email": "john@example.com",
                     "description": "   ", "social_links": ["  https://linkedin.com/in/jd  "]}""")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.first_name").value("John"))
                    .andExpect(jsonPath("$.last_name").value("Doe"))
                    .andExpect(jsonPath("$.description").doesNotExist())
                    .andExpect(jsonPath("$.social_links[0]").value("https://linkedin.com/in/jd"));
        }

        @Test
        void clientAcceptsUpTo20SocialLinks() throws Exception {
            postJson("/clients", clientWithLinks("twenty@example.com", 20)).andExpect(status().isCreated());
            postJson("/clients", clientWithLinks("many@example.com", 21))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.social_links").exists());
        }

        @Test
        void clientRejectsBlankSocialLink() throws Exception {
            postJson("/clients", """
                    {"first_name": "A", "last_name": "B", "email": "a@example.com", "social_links": ["  "]}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors['social_links[0]']").exists());
        }

        @Test
        void clientRejectsOverlongFields() throws Exception {
            postJson("/clients", """
                    {"first_name": "%s", "last_name": "B", "email": "a@example.com", "description": "%s"}"""
                    .formatted("x".repeat(101), "x".repeat(10_001)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.first_name").exists())
                    .andExpect(jsonPath("$.errors.description").exists());
        }

        @Test
        void rejectsMalformedJson() throws Exception {
            postJson("/clients", "{not json").andExpect(status().isBadRequest());
        }

        @Test
        void documentRequiresTitleAndContent() throws Exception {
            String clientId = createClient("John", "Doe", "john@example.com", "");
            postJson("/clients/" + clientId + "/documents", """
                    {"title": "", "content": "  "}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.title").exists())
                    .andExpect(jsonPath("$.errors.content").exists());
        }

        @Test
        void documentTitleAndContentAreTrimmed() throws Exception {
            String clientId = createClient("John", "Doe", "john@example.com", "");
            postJson("/clients/" + clientId + "/documents", """
                    {"title": "  Will  ", "content": "  Last will.  "}""")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("Will"))
                    .andExpect(jsonPath("$.content").value("Last will."));
        }

        @Test
        void documentRejectsOverlongTitleAndContent() throws Exception {
            String clientId = createClient("John", "Doe", "john@example.com", "");
            postJson("/clients/" + clientId + "/documents", """
                    {"title": "%s", "content": "%s"}""".formatted("x".repeat(501), "x".repeat(200_001)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.title").exists())
                    .andExpect(jsonPath("$.errors.content").exists());
        }

        @Test
        void searchRequiresANonBlankQueryOfAtMost500Characters() throws Exception {
            mvc.perform(get("/search")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.q").exists());
            mvc.perform(get("/search").param("q", "  ")).andExpect(status().isBadRequest());
            mvc.perform(get("/search").param("q", "x".repeat(501))).andExpect(status().isBadRequest());
        }

        private String clientWithLinks(String email, int count) {
            String links = IntStream.range(0, count).mapToObj(i -> "\"https://example.com/" + i + "\"")
                    .collect(Collectors.joining(", "));
            return """
                    {"first_name": "A", "last_name": "B", "email": "%s", "social_links": [%s]}""".formatted(email, links);
        }
    }
}
