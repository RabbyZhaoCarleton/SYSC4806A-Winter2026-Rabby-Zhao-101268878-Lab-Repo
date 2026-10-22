package org.carleton;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI controller. Unlike a @RestController, a plain @Controller method returns the
 * NAME of a view; Thymeleaf resolves "addressbook" to
 * src/main/resources/templates/addressbook.html and renders it with the Model's data.
 *
 * The POST handlers below receive regular HTML form submissions
 * (application/x-www-form-urlencoded) and answer with a redirect, so the browser
 * reloads the page with a GET afterwards (Post/Redirect/Get).
 *
 *   GET  /                                   table of all address books
 *   POST /view/addressbooks                  create a new address book, redirect to /
 *   GET  /view/addressbooks/{bookId}         table of the book's buddies + "add buddy" form
 *   POST /view/addressbooks/{bookId}/buddies create a buddy in the book, redirect back
 */
@Controller
public class AddressBookViewController {

    private final AddressBookRepository addressBookRepository;

    public AddressBookViewController(AddressBookRepository addressBookRepository) {
        this.addressBookRepository = addressBookRepository;
    }

    /** GET / -> homepage with a table of every address book, each linking to its view page. */
    @GetMapping("/")
    public String home(Model model) {
        List<AddressBook> books = new ArrayList<>();
        addressBookRepository.findAll().forEach(books::add);
        model.addAttribute("addressBooks", books);
        return "index";
    }

    /** POST /view/addressbooks -> creates an empty address book, then goes back to the homepage. */
    @PostMapping("/view/addressbooks")
    public String createAddressBook() {
        addressBookRepository.save(new AddressBook());
        return "redirect:/";
    }

    /** GET /view/addressbooks/{bookId} -> HTML page listing that book's buddies. */
    @GetMapping("/view/addressbooks/{bookId}")
    public String viewAddressBook(@PathVariable long bookId, Model model) {
        model.addAttribute("addressBook", findBookOr404(bookId));
        model.addAttribute("newBuddy", new BuddyInfo());
        return "addressbook";
    }

    /**
     * POST /view/addressbooks/{bookId}/buddies -> the form fields (name, email, ...) are bound
     * onto a new BuddyInfo, which is added to the book. Redirects back to the book's page.
     */
    @PostMapping("/view/addressbooks/{bookId}/buddies")
    public String addBuddy(@PathVariable long bookId, @ModelAttribute("newBuddy") BuddyInfo buddy) {
        AddressBook book = findBookOr404(bookId);
        buddy.setId(null); // let the database generate the id
        book.addBuddy(buddy);
        addressBookRepository.save(book); // cascade = ALL saves the new buddy too
        return "redirect:/view/addressbooks/" + bookId;
    }

    private AddressBook findBookOr404(long id) {
        AddressBook book = addressBookRepository.findById(id);
        if (book == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AddressBook " + id + " not found");
        }
        return book;
    }
}
