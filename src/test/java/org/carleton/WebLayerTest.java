package org.carleton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@WebMvcTest({AddressBookController.class, BuddyInfoController.class, AddressBookViewController.class})
@AutoConfigureRestTestClient
class WebLayerTest {

    @Autowired
    private RestTestClient restTestClient;

    @MockitoBean
    private AddressBookRepository addressBookRepository;

    @MockitoBean
    private BuddyInfoRepository buddyRepository;

    private AddressBook book;
    private BuddyInfo rabby;

    @BeforeEach
    void setUp() {
        rabby = new BuddyInfo("Rabby", "Rabby@gmail.com", "123-456-7890", "123 Main St, Carleton, ON");
        rabby.setId(10L);
        book = new AddressBook();
        book.setId(1L);

        // save(...) behaves like a real repository: give new objects an id and return them
        when(addressBookRepository.save(any(AddressBook.class))).thenAnswer(invocation -> {
            AddressBook saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(42L);
            }
            return saved;
        });
        when(buddyRepository.save(any(BuddyInfo.class))).thenAnswer(invocation -> {
            BuddyInfo saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });
    }

    @Test
    void postAddressBookReturnsCreatedBook() {
        restTestClient.post().uri("/addressbooks")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(42)
                .jsonPath("$.buddyList.length()").isEqualTo(0);

        verify(addressBookRepository).save(any(AddressBook.class));
    }

    @Test
    void getAddressBookReturnsJsonFromRepository() {
        book.addBuddy(rabby);
        when(addressBookRepository.findById(1L)).thenReturn(book);

        restTestClient.get().uri("/addressbooks/1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.buddyList[0].id").isEqualTo(10)
                .jsonPath("$.buddyList[0].name").isEqualTo("Rabby")
                .jsonPath("$.buddyList[0].email").isEqualTo("Rabby@gmail.com");
    }

    @Test
    void getAllAddressBooks() {
        AddressBook other = new AddressBook();
        other.setId(2L);
        when(addressBookRepository.findAll()).thenReturn(List.of(book, other));

        restTestClient.get().uri("/addressbooks")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo(1)
                .jsonPath("$[1].id").isEqualTo(2);
    }

    @Test
    void getMissingAddressBookReturns404() {
        when(addressBookRepository.findById(5L)).thenReturn(null);

        restTestClient.get().uri("/addressbooks/5")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void postBuddyIntoAddressBookAddsAndSaves() {
        when(addressBookRepository.findById(1L)).thenReturn(book);

        restTestClient.post().uri("/addressbooks/1/buddies")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"id":123,"name":"Jane","email":"Jane@gmail.com","phone":"987-654-3210","address":"456 Elm St"}""")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.buddyList.length()").isEqualTo(1)
                .jsonPath("$.buddyList[0].name").isEqualTo("Jane");

        // The controller must ignore any client-supplied id and save the book with the new buddy.
        verify(addressBookRepository).save(argThat(saved ->
                saved.getBuddyList().size() == 1 && saved.getBuddyList().get(0).getId() == null));
    }

    @Test
    void putExistingBuddyAddsItOnlyOnce() {
        book.addBuddy(rabby);
        when(addressBookRepository.findById(1L)).thenReturn(book);
        when(buddyRepository.findById(10L)).thenReturn(rabby);

        restTestClient.put().uri("/addressbooks/1/buddies/10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.buddyList.length()").isEqualTo(1);
    }

    @Test
    void putUnknownBuddyReturns404AndDoesNotSave() {
        when(addressBookRepository.findById(1L)).thenReturn(book);
        when(buddyRepository.findById(77L)).thenReturn(null);

        restTestClient.put().uri("/addressbooks/1/buddies/77")
                .exchange()
                .expectStatus().isNotFound();

        verify(addressBookRepository, never()).save(any(AddressBook.class));
    }

    @Test
    void deleteBuddyRemovesItFromBook() {
        book.addBuddy(rabby);
        when(addressBookRepository.findById(1L)).thenReturn(book);
        when(buddyRepository.findById(10L)).thenReturn(rabby);

        restTestClient.delete().uri("/addressbooks/1/buddies/10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.buddyList.length()").isEqualTo(0);
    }

    @Test
    void postStandaloneBuddy() {
        restTestClient.post().uri("/buddies")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new BuddyInfo("Bob", "Bob@gmail.com", "555-555-5555", "789 Oak St"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(BuddyInfo.class)
                .consumeWith(result -> {
                    BuddyInfo body = result.getResponseBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getId()).isEqualTo(99L);
                    assertThat(body.getName()).isEqualTo("Bob");
                });
    }

    @Test
    void getBuddyAndMissingBuddy() {
        when(buddyRepository.findById(10L)).thenReturn(rabby);
        when(buddyRepository.findById(11L)).thenReturn(null);

        restTestClient.get().uri("/buddies/10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Rabby");

        restTestClient.get().uri("/buddies/11")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void thymeleafViewRendersBuddiesFromRepository() {
        book.addBuddy(rabby);
        when(addressBookRepository.findById(1L)).thenReturn(book);

        restTestClient.get().uri("/view/addressbooks/1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .contains("Address Book #1")
                        .contains("<td>Rabby</td>")
                        .contains("Rabby@gmail.com")
                        .contains("123 Main St, Carleton, ON"));
    }

    @Test
    void nicknameAppearsInJsonAndView() {
        rabby.setNickname("Rab");
        book.addBuddy(rabby);
        when(addressBookRepository.findById(1L)).thenReturn(book);

        restTestClient.get().uri("/addressbooks/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.buddyList[0].nickname").isEqualTo("Rab");

        restTestClient.get().uri("/view/addressbooks/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .contains("<th>Nickname</th>")
                        .contains("<td>Rab</td>"));
    }

    @Test
    void homepageListsAddressBooks() {
        book.addBuddy(rabby);
        AddressBook other = new AddressBook();
        other.setId(2L);
        when(addressBookRepository.findAll()).thenReturn(List.of(book, other));

        restTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .contains("<th>ID</th>")
                        .contains("href=\"/view/addressbooks/1\"")
                        .contains("Address Book #1")
                        .contains("<td>1</td>")
                        .contains("href=\"/view/addressbooks/2\"")
                        .contains("Address Book #2")
                        .contains("<td>0</td>")
                        .contains("action=\"/view/addressbooks\"")
                        .doesNotContain("No address books yet."));
    }

    @Test
    void homepageWithNoAddressBooksShowsMessage() {
        when(addressBookRepository.findAll()).thenReturn(List.of());

        restTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .contains("No address books yet.")
                        .contains("Create New Address Book")
                        .doesNotContain("<table"));
    }

    @Test
    void createAddressBookFormRedirectsToHomepage() {
        restTestClient.post().uri("/view/addressbooks")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().location("/");

        verify(addressBookRepository).save(any(AddressBook.class));
    }

    @Test
    void addressBookPageHasAddBuddyForm() {
        when(addressBookRepository.findById(1L)).thenReturn(book);

        restTestClient.get().uri("/view/addressbooks/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .contains("action=\"/view/addressbooks/1/buddies\"")
                        .contains("name=\"name\"")
                        .contains("name=\"email\"")
                        .contains("name=\"phone\"")
                        .contains("name=\"address\"")
                        .contains("name=\"nickname\"")
                        .contains("href=\"/\""));
    }

    @Test
    void addBuddyFormAddsBuddyAndRedirectsBack() {
        when(addressBookRepository.findById(1L)).thenReturn(book);

        restTestClient.post().uri("/view/addressbooks/1/buddies")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("name=Jane&email=Jane%40gmail.com&phone=987-654-3210&address=456+Elm+St&nickname=JJ")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().location("/view/addressbooks/1");

        verify(addressBookRepository).save(argThat(saved ->
                saved.getBuddyList().size() == 1
                        && "Jane".equals(saved.getBuddyList().get(0).getName())
                        && "Jane@gmail.com".equals(saved.getBuddyList().get(0).getEmail())
                        && "456 Elm St".equals(saved.getBuddyList().get(0).getAddress())
                        && "JJ".equals(saved.getBuddyList().get(0).getNickname())));
    }

    @Test
    void addBuddyFormToMissingBookReturns404() {
        when(addressBookRepository.findById(5L)).thenReturn(null);

        restTestClient.post().uri("/view/addressbooks/5/buddies")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("name=Ghost")
                .exchange()
                .expectStatus().isNotFound();

        verify(addressBookRepository, never()).save(any(AddressBook.class));
    }
}
