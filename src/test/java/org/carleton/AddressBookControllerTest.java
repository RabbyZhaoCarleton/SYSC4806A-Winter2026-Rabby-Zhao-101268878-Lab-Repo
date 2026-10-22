package org.carleton;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest
@AutoConfigureRestTestClient
class AddressBookControllerTest {

    @Autowired
    private RestTestClient restTestClient;

    private long createBook() {
        AddressBook book = restTestClient.post().uri("/addressbooks")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AddressBook.class)
                .returnResult().getResponseBody();
        assertThat(book).isNotNull();
        assertThat(book.getId()).isNotNull();
        assertThat(book.getBuddyList()).isEmpty();
        return book.getId();
    }

    private long createStandaloneBuddy(BuddyInfo buddy) {
        BuddyInfo created = restTestClient.post().uri("/buddies")
                .contentType(MediaType.APPLICATION_JSON)
                .body(buddy)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(BuddyInfo.class)
                .returnResult().getResponseBody();
        assertThat(created).isNotNull();
        return created.getId();
    }

    // requests.http #1 / #2: POST /addressbooks
    @Test
    void createAddressBook() {
        long bookId = createBook();

        restTestClient.get().uri("/addressbooks/" + bookId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(bookId)
                .jsonPath("$.buddyList.length()").isEqualTo(0);
    }

    // requests.http #3 / #4: POST /addressbooks/{id}/buddies with a JSON body
    @Test
    void createBuddiesInsideAddressBook() {
        long bookId = createBook();

        restTestClient.post().uri("/addressbooks/" + bookId + "/buddies")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"Rabby","email":"Rabby@gmail.com","phone":"123-456-7890","address":"123 Main St, Carleton, ON"}""")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.buddyList.length()").isEqualTo(1)
                .jsonPath("$.buddyList[0].id").exists()
                .jsonPath("$.buddyList[0].name").isEqualTo("Rabby");

        restTestClient.post().uri("/addressbooks/" + bookId + "/buddies")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new BuddyInfo("Jane", "Jane@gmail.com", "987-654-3210", "456 Elm St, Toronto, ON"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.buddyList.length()").isEqualTo(2)
                .jsonPath("$.buddyList[1].name").isEqualTo("Jane")
                .jsonPath("$.buddyList[1].email").isEqualTo("Jane@gmail.com")
                .jsonPath("$.buddyList[1].phone").isEqualTo("987-654-3210")
                .jsonPath("$.buddyList[1].address").isEqualTo("456 Elm St, Toronto, ON");
    }

    // requests.http #5: POST /buddies, then GET /buddies/{id}
    @Test
    void createAndGetStandaloneBuddy() {
        long buddyId = createStandaloneBuddy(new BuddyInfo("Bob", "Bob@gmail.com", "555-555-5555", "789 Oak St, Ottawa, ON"));

        restTestClient.get().uri("/buddies/" + buddyId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(BuddyInfo.class)
                .consumeWith(result -> {
                    BuddyInfo body = result.getResponseBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getId()).isEqualTo(buddyId);
                    assertThat(body.getName()).isEqualTo("Bob");
                    assertThat(body.getAddress()).isEqualTo("789 Oak St, Ottawa, ON");
                });
    }

    // requests.http #6: PUT /addressbooks/{bookId}/buddies/{buddyId}
    @Test
    void addExistingBuddyToAddressBook() {
        long bookId = createBook();
        long buddyId = createStandaloneBuddy(new BuddyInfo("Bob", "Bob@gmail.com", "555-555-5555", "789 Oak St, Ottawa, ON"));

        restTestClient.put().uri("/addressbooks/" + bookId + "/buddies/" + buddyId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.buddyList.length()").isEqualTo(1)
                .jsonPath("$.buddyList[0].id").isEqualTo(buddyId)
                .jsonPath("$.buddyList[0].name").isEqualTo("Bob");

        // Adding the same buddy twice must not duplicate it.
        restTestClient.put().uri("/addressbooks/" + bookId + "/buddies/" + buddyId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.buddyList.length()").isEqualTo(1);
    }

    // DELETE /addressbooks/{bookId}/buddies/{buddyId}
    @Test
    void removeBuddyFromAddressBook() {
        long bookId = createBook();
        long buddyId = createStandaloneBuddy(new BuddyInfo("Alice", "Alice@gmail.com", "613-555-0199", "1125 Colonel By Dr, Ottawa, ON"));

        restTestClient.put().uri("/addressbooks/" + bookId + "/buddies/" + buddyId)
                .exchange()
                .expectStatus().isOk();

        restTestClient.delete().uri("/addressbooks/" + bookId + "/buddies/" + buddyId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.buddyList.length()").isEqualTo(0);
    }

    // requests.http #8 / #10: GET /addressbooks and GET /buddies
    @Test
    void listAllAddressBooksAndBuddies() {
        long bookId = createBook();
        restTestClient.post().uri("/addressbooks/" + bookId + "/buddies")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new BuddyInfo("Listed", "Listed@gmail.com", "111-222-3333", "1 Test Ave"))
                .exchange()
                .expectStatus().isCreated();

        restTestClient.get().uri("/addressbooks")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.id == " + bookId + ")].buddyList[0].name").value(List.class, names -> assertThat(names).contains("Listed"));

        restTestClient.get().uri("/buddies")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.email == 'Listed@gmail.com')].name").value(List.class, names -> assertThat(names).contains("Listed"));
    }

    // requests.http #11 / #12: GET /view/addressbooks/{id} (Thymeleaf HTML page)
    @Test
    void thymeleafViewListsBuddies() {
        long bookId = createBook();
        restTestClient.post().uri("/addressbooks/" + bookId + "/buddies")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new BuddyInfo("Rabby", "Rabby@gmail.com", "123-456-7890", "123 Main St, Carleton, ON"))
                .exchange()
                .expectStatus().isCreated();

        restTestClient.get().uri("/view/addressbooks/" + bookId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .contains("Address Book #" + bookId)
                        .contains("<td>Rabby</td>")
                        .contains("Rabby@gmail.com")
                        .contains("123-456-7890"));
    }

    @Test
    void emptyAddressBookViewShowsMessage() {
        long bookId = createBook();
        restTestClient.get().uri("/view/addressbooks/" + bookId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .contains("This address book has no buddies yet."));
    }

    @Test
    void unknownIdsReturn404() {
        restTestClient.get().uri("/addressbooks/999999").exchange().expectStatus().isNotFound();
        restTestClient.get().uri("/buddies/999999").exchange().expectStatus().isNotFound();
        restTestClient.get().uri("/view/addressbooks/999999").exchange().expectStatus().isNotFound();

        long bookId = createBook();
        restTestClient.put().uri("/addressbooks/" + bookId + "/buddies/999999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void nicknameIsSavedAndShownInJsonAndView() {
        long bookId = createBook();

        restTestClient.post().uri("/addressbooks/" + bookId + "/buddies")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"Rabby","email":"Rabby@gmail.com","phone":"123-456-7890","address":"123 Main St, Carleton, ON","nickname":"Rab"}""")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.buddyList[0].nickname").isEqualTo("Rab");

        restTestClient.get().uri("/addressbooks/" + bookId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.buddyList[0].nickname").isEqualTo("Rab");

        restTestClient.get().uri("/view/addressbooks/" + bookId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .contains("<th>Nickname</th>")
                        .contains("<td>Rab</td>"));
    }

    @Test
    void homepageLinksToEachAddressBook() {
        long bookId = createBook();
        restTestClient.post().uri("/addressbooks/" + bookId + "/buddies")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new BuddyInfo("Rabby", "Rabby@gmail.com", "123-456-7890", "123 Main St, Carleton, ON"))
                .exchange()
                .expectStatus().isCreated();

        restTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .contains("<h1>Address Books</h1>")
                        .contains("href=\"/view/addressbooks/" + bookId + "\"")
                        .contains("Address Book #" + bookId + "</a>")
                        .contains("<td>1</td>"));
    }
}
