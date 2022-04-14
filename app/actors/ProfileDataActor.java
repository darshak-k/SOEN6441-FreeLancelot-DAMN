package actors;

/**
 * Profile Data Actor
 * 
 * @author Apekshaba Gohil
 */

import akka.actor.AbstractActor;
import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import models.*;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;

import models.APIResponse;
import models.SearchResult;
import play.mvc.Result;
import views.html.profile;

import javax.inject.Inject;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static play.mvc.Results.redirect;

public class ProfileDataActor extends AbstractActor {
    public static List<SearchProfile> ProfileResults = new ArrayList<>();
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final WSClient ws;
    private static final int RESULT_COUNT = 10;
    private static String baseURL = "https://www.freelancer.com/api";
    private ObjectMapper mapper;
    HashMap<List<SearchResult>, List<SearchProfile>> profileMap = new HashMap<>();

    @Inject
    public ProfileDataActor(WSClient ws) {
        this.ws = ws;
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public static Props props(WSClient ws) {
        return Props.create(ProfileDataActor.class, ws);
    }

    @Override
    public void preStart() {

        log.info("ProfileDataActor {}-{} started at ", this, LocalTime.now());
    }

    @Override
    public void postStop() {

        log.info("ProfileDataActor {}-{} stopped at ", this, LocalTime.now());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Messages.ProfileDataActorMessage.class, this::searchOwnerData)
                .build();
    }

    private void searchOwnerData(Messages.ProfileDataActorMessage queryProfile) {
        final ActorRef senderRef = sender();
        ws.url(baseURL + "/users/0.1/users/" + String.valueOf(queryProfile.ownerId))
                .addHeader("freelancer-oauth-v1", "l12Bz0qvwEkZVSvwzFds2EBSGGhDqa")
                .addQueryParameter("chosenrole", "true")
                .addQueryParameter("limitedaccount", "true")
                .addQueryParameter("query", String.valueOf(queryProfile.ownerId))
                .addQueryParameter("limit", String.valueOf(RESULT_COUNT))
                .addQueryParameter("compact", "true")
                .get()
                .thenApplyAsync(WSResponse::asJson)
                .toCompletableFuture()
                .thenApplyAsync(result1 -> {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

                    try {
                        ProfileResponce profile = mapper.treeToValue(result1, ProfileResponce.class);
                        SearchProfile searchProfile = new SearchProfile();
                        searchProfile.setQuery(String.valueOf(queryProfile.ownerId));
                        searchProfile.setProfiledata(profile.getResult());
                        ProfileResults.clear();
                        ProfileResults.add(0, searchProfile);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }

                    return ProfileResults;
                }).thenAccept(

                        response -> {
                            senderRef.tell(response, self());
                        });
    }
}