package fr.istic.tlc.resources;

import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.istic.tlc.dao.ChoiceRepository;
import fr.istic.tlc.dao.PollRepository;
import fr.istic.tlc.dao.UserRepository;
import fr.istic.tlc.domain.Choice;
import fr.istic.tlc.domain.Poll;
import fr.istic.tlc.domain.User;
import io.quarkus.panache.common.Sort;

@RestController
@RequestMapping("/api")
public class UserResource {

	private static final Logger LOG = Logger.getLogger(UserResource.class);

	@Autowired
	ChoiceRepository choiceRepository;
	@Autowired
	PollRepository pollRepository;
	@Autowired
	UserRepository userRepository;

	@GetMapping("/users")
	public ResponseEntity<List<User>> retrieveAllUsers() {
		LOG.info("Retrieve all users request");
		// On récupère tous les utilisateurs qu'on trie ensuite par username
		List<User> users = userRepository.findAll(Sort.by( "username", Sort.Direction.Ascending)).list();
		
		LOG.infof("Found %d users", users.size());
		
		return new ResponseEntity<>(users, HttpStatus.OK);
	}

	@GetMapping("/users/{idUser}")
	public ResponseEntity<User> retrieveUser(@PathVariable long idUser) {
		
		LOG.infof("Retrieving user with ID: %d", idUser);
		
		// On vérifie que l'utilisateur existe
		User user = userRepository.findById(idUser);
		if (user== null) {
			LOG.warnf("User with ID: %d not found", idUser);
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		LOG.infof("Found user: %s", user);
		return new ResponseEntity<>(user, HttpStatus.OK);
	}

	@GetMapping("/polls/{slug}/users")
	public ResponseEntity<List<User>> getAllUserFromPoll(@PathVariable String slug) {
		
		LOG.infof("Retrieving all users from poll with slug: %s", slug);

		List<User> users = new ArrayList<>();
		// On vérifie que le poll existe
		Poll poll = pollRepository.findBySlug(slug);
		if (poll== null) {
			LOG.warnf("Poll with slug: %s not found", slug);
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		// On parcours les choix du poll pour récupérer les users ayant voté
		if (!poll.getPollChoices().isEmpty()) {
			for (Choice choice : poll.getPollChoices()) {
				if (!choice.getUsers().isEmpty()) {
					for (User user : choice.getUsers()) {
						// On vérifie que le user ne soit pas déjà dans la liste
						if (!users.contains(user)) {
							users.add(user);
						}
					}
				}
			}
		}

		LOG.infof("Found %d users who voted", users.size());
		return new ResponseEntity<>(users, HttpStatus.OK);
	}

	@DeleteMapping("/users/{idUser}")
	public ResponseEntity<User> deleteUser(@PathVariable long idUser) {

		LOG.infof("Deleting user with ID: %d", idUser);

		// On vérifie que l'utilisateur existe
		User user = userRepository.findById(idUser);
		if (user== null) {
			LOG.warnf("User with ID: %d not found", idUser);
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		// On supprime l'utilisateur de la liste d'utilisateur de chaque choix
		for (Choice choice : user.getUserChoices()) {
			choice.removeUser(user);
			choiceRepository.getEntityManager().merge(choice);
		}
		// On supprime les commentaires de l'utilisateurs
		// Fait automatiquement par le cascade type ALL

		// On supprime l'utilisateur de la bdd
		userRepository.deleteById(idUser);
		LOG.info("User deleted successfully");
		return new ResponseEntity<>(user, HttpStatus.OK);
	}

	@PostMapping("/users")
	public ResponseEntity<User> createUser(@Valid @RequestBody User user) {
		// On sauvegarde l'utilisateur dans la bdd
		userRepository.persist(user);
		LOG.infof("User created with ID: %d", user.getId());
		return new ResponseEntity<>(user, HttpStatus.CREATED);
	}

	@PutMapping("/users/{idUser}")
	public ResponseEntity<User> updateUser(@PathVariable long idUser, @Valid @RequestBody User user) {
		LOG.infof("Updating user with ID: %d", idUser);
		// On vérifie que l'utilisateur existe
		User optionalUser = userRepository.findById(idUser);
		if (optionalUser== null) {
			LOG.warnf("User with ID: %d not found", idUser);
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		// On met le bon id sur l'utilisateur
		User ancientUser = optionalUser;
		if (user.getUsername() != null) {
			ancientUser.setUsername(user.getUsername());
		}
		// On update l'utilisateur dans la bdd
		User updatedUser = userRepository.getEntityManager().merge(ancientUser);
		
		LOG.infof("User updated with ID: %d", updatedUser.getId());
		return new ResponseEntity<>(updatedUser, HttpStatus.OK);
	}
}
