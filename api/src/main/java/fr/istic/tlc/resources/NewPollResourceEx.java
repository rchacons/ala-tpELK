package fr.istic.tlc.resources;

import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import fr.istic.tlc.dao.ChoiceRepository;
import fr.istic.tlc.dao.CommentRepository;
import fr.istic.tlc.dao.MealPreferenceRepository;
import fr.istic.tlc.dao.PollRepository;
import fr.istic.tlc.dao.UserRepository;
import fr.istic.tlc.domain.Choice;
import fr.istic.tlc.domain.Comment;
import fr.istic.tlc.domain.MealPreference;
import fr.istic.tlc.domain.Poll;
import fr.istic.tlc.domain.User;
import fr.istic.tlc.dto.ChoiceUser;
import fr.istic.tlc.services.SendEmail;

@Path("/api/poll")
public class NewPollResourceEx {

	private static final Logger LOG = Logger.getLogger(NewPollResourceEx.class);

	@Inject
	PollRepository pollRep;

	@Inject
	UserRepository userRep;

	@Inject
	ChoiceRepository choiceRep;

	@Inject
	MealPreferenceRepository mealprefRep;

	@Inject
	CommentRepository commentRep;
	
	@Inject
	SendEmail sendmail;

	@Path("/slug/{slug}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Poll getPollBySlug(@PathParam("slug") String slug) {
		
		LOG.warnf("Poll with slug: %s not found", slug);
		Poll p = pollRep.findBySlug(slug);
		if (p != null){
			p.getPollComments().clear();
			LOG.info("Poll retrieved and comments cleared");
		} else{
			LOG.warnf("Poll with slug: %s not found", slug);
		}
		p.setSlugAdmin("");
		return p;
	}

	@Path("/aslug/{aslug}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Poll getPollByASlug(@PathParam("aslug") String aslug) {
		LOG.infof("Retrieving poll by admin slug: %s", aslug);
		return pollRep.findByAdminSlug(aslug);
	}

	@Path("/comment/{slug}")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Transactional
	@Produces(MediaType.APPLICATION_JSON)
	public Comment createComment4Poll(@PathParam("slug") String slug, Comment c) {
		LOG.infof("Creating comment for poll with slug: %s", slug);
		
		this.commentRep.persist(c);
		Poll p = pollRep.findBySlug(slug);
		p.addComment(c);
		this.pollRep.persistAndFlush(p);

		LOG.info("Comment created and added to poll");
		return c;

	}

	@PUT
	@Path("/update1")
	@Consumes(MediaType.APPLICATION_JSON)
	@Transactional
	@Produces(MediaType.APPLICATION_JSON)
	public Poll updatePoll(Poll p) {
		LOG.infof("Updating poll with ID: %d", p.getId());

		System.err.println( "p " + p);
		Poll p1 = pollRep.findById(p.getId());
		List<Choice> choicesToRemove = new ArrayList<Choice>();
		for (Choice c : p1.getPollChoices()) {
			if (!p.getPollChoices().contains(c)) {
				choicesToRemove.add(c);
			}

		}
		for (Choice c : p.getPollChoices()) {
			if (c.getId() != null) {
				this.choiceRep.getEntityManager().merge(c);
			} else {
				this.choiceRep.getEntityManager().persist(c);
			}

		}
		for (Choice c : choicesToRemove) {
			if (c.equals(p1.getSelectedChoice())) {
				p.setSelectedChoice(null);
				p1.setSelectedChoice(null);
				p.setClos(false);
			}
			for (User u : c.getUsers()) {
				u.getUserChoices().remove(c);
			}
			c.getUsers().clear();
			this.choiceRep.delete(c);
			LOG.debugf("Choice with ID: %d removed", c.getId());

		}

		for (Choice c : p.getPollChoices()) {
			LOG.debugf("Choice with ID: %d ready for merge", c.getId());
		}

		Poll p2 = this.pollRep.getEntityManager().merge(p);
		LOG.infof("Poll %s updated", p2.getId());
		return p2;

	}

	@Path("/choiceuser")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Transactional
	public User addChoiceUser(ChoiceUser userChoice) {

		LOG.infof("Adding choices to user : %s", userChoice.getUsername());

		User u = this.userRep.find("mail", userChoice.getMail()).firstResult();
		if (u == null) {
			u = new User();
			u.setUsername(userChoice.getUsername());
			u.setIcsurl(userChoice.getIcs());
			u.setMail(userChoice.getMail());
			this.userRep.persist(u);
		}
		

		if (userChoice.getPref() != null && !"".equals(userChoice.getPref())) {
			MealPreference mp = new MealPreference();
			mp.setContent(userChoice.getPref());
			mp.setUser(u);
			this.mealprefRep.persist(mp);
		}
		for (Long choiceId : userChoice.getChoices()) {
			Choice c = this.choiceRep.findById(choiceId);
			c.addUser(u);
			this.choiceRep.persistAndFlush(c);
		}
		LOG.infof("Choices added to user %s",userChoice.getUsername());
		return u;
	}

	@Path("/selectedchoice/{choiceid}")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Transactional
	public void closePoll(@PathParam("choiceid") String choiceid) {

		LOG.infof("Closing poll: %s", choiceid);
		Choice c = choiceRep.findById(Long.parseLong(choiceid));
		Poll p = this.pollRep.find("select p from Poll as p join p.pollChoices as c where c.id= ?1", c.getId())
				.firstResult();
		p.setClos(true);
		p.setSelectedChoice(c);
		this.pollRep.persist(p);
		this.sendmail.sendASimpleEmail(p);
		LOG.infof("Poll %s closed and email sent", choiceid);

		// TODO Send Email

	}

	@GET()
	@Path("polls/{slug}/comments")
	@Produces(MediaType.APPLICATION_JSON)
	public List<Comment> getAllCommentsFromPoll(@PathParam("slug") String slug) {

		LOG.infof("Retrieving all comments from poll with slug: %s", slug);
		Poll p = this.pollRep.findBySlug(slug);
		if (p!= null){
			List<Comment> comments = p.getPollComments();
			LOG.infof("Found %d comments", comments.size());
			return comments;
		}
		
		LOG.warnf("Poll with slug: %s not found", slug);
		return null;
	}

}
