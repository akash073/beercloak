package beercloak.resources;

import beercloak.Drunkenness;
import beercloak.dto.UserDto;
import beercloak.models.jpa.entities.BeerEntity;
import beercloak.representations.BeerRepresentation;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.keycloak.events.admin.OperationType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.services.ErrorResponse;
import org.keycloak.services.managers.AppAuthManager;

/**
 * @author <a href="mailto:mitya@cargosoft.ru">Dmitry Telegin</a>
 */
public class BeerResource extends AbstractAdminResource<BeerAdminAuth> {

    @Context
    private KeycloakSession session;

    private final EntityManager em;

    private final String defaultAttr = "merchent_id";
    Logger logger = Logger.getLogger(this.getClass().getName());

    public BeerResource(RealmModel realm, EntityManager em) {
        super(realm);
        this.em = em;
    }

    @GET
    @NoCache
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public BeerRepresentation get(final @PathParam("id") String id) {

        auth.checkViewBeer();

        BeerEntity beer = find(id);
        return toRepresentation(beer);

    }

    @GET
    @NoCache
    @Produces(MediaType.APPLICATION_JSON)
    public List<BeerRepresentation> list(@QueryParam("search") String search,
            @QueryParam("first") Integer firstResult,
            @QueryParam("max") Integer maxResults) {

        auth.checkViewBeer();

        ArrayList<BeerRepresentation> beers = new ArrayList<>();

        TypedQuery<BeerEntity> query;

        if (search != null) {
            query = em.createNamedQuery("findBeers", BeerEntity.class);
            query.setParameter("search", "%" + search + "%");
        } else {
            query = em.createNamedQuery("findAllBeers", BeerEntity.class);
        }

        query.setParameter("realmId", realm.getId());

        if (firstResult != null) {
            query.setFirstResult(firstResult);
        }
        if (maxResults != null) {
            query.setMaxResults(maxResults);
        }

        List<BeerEntity> results = query.getResultList();

        results.forEach((entity) -> {
            beers.add(toRepresentation(entity));
        });

        return beers;

    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(final @Context UriInfo uriInfo, final BeerRepresentation rep) {

        auth.checkManageBeer();

        BeerEntity beer = new BeerEntity();

        beer.setId(KeycloakModelUtils.generateId());
        beer.setRealmId(realm.getId());
        beer.setName(rep.getName());
        beer.setType(rep.getType());
        beer.setAbv(rep.getAbv());

        try {
            em.persist(beer);
            em.flush();
            
            adminEvent
                    .operation(OperationType.CREATE)
//                    .resource(ResourceType.of("BEER"))
                    .resourcePath(uriInfo, beer.getId())
                    .representation(rep)
                    .success();
            
            if (session.getTransactionManager().isActive()) {
                session.getTransactionManager().commit();
            }
            
            return Response.created(uriInfo.getAbsolutePathBuilder().path(beer.getId()).build()).build();
            
        } catch (ModelDuplicateException e) {
            if (session.getTransactionManager().isActive()) {
                session.getTransactionManager().setRollbackOnly();
            }
            return ErrorResponse.exists("Beer exists with the same name");
        }

    }

    @Path("{id}")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(final @Context UriInfo uriInfo, final @PathParam("id") String id, final BeerRepresentation rep) {

        auth.checkManageBeer();

        BeerEntity beer = find(id);

        if (rep.getName() != null) beer.setName(rep.getName());
        if (rep.getType() != null) beer.setType(rep.getType());
        if (rep.getAbv() != null) beer.setAbv(rep.getAbv());

        em.flush();

        adminEvent
                .operation(OperationType.UPDATE)
//                .resource(ResourceType.of("BEER"))
                .resourcePath(uriInfo)
                .representation(rep)
                .success();

        if (session.getTransactionManager().isActive()) {
            session.getTransactionManager().commit();
        }

        return Response.noContent().build();

    }

    @Path("{id}")
    @DELETE
    @NoCache
    public Response delete(final @Context UriInfo uriInfo, final @PathParam("id") String id) {

        auth.checkManageBeer();

        BeerEntity beer = find(id);

        em.remove(beer);
        em.flush();

        adminEvent
                .operation(OperationType.DELETE)
//                .resource(ResourceType.of("BEER"))
                .resourcePath(uriInfo)
                .success();

        if (session.getTransactionManager().isActive()) {
            session.getTransactionManager().commit();
        }

        return Response.noContent().build();

    }

    @Path("{id}/drink")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String[] drink(final @Context UriInfo uriInfo, final @PathParam("id") String id, Integer[] _qty) {

        auth.checkManageBeer();

        BeerEntity beer = find(id);

        int qty = _qty[0];

        Drunkenness d = Drunkenness.drunk(beer.getAbv(), qty);

        adminEvent
                .operation(OperationType.ACTION)
//                .resource(ResourceType.of("BEER"))
                .resourcePath(uriInfo)
                .success();

        return new String[] { d.toString() };

    }

    @Path("types")
    public BeerTypeResource types() {
        return new BeerTypeResource();
    }

    private BeerEntity find(String id) {

        try {
            BeerEntity beer = em.createNamedQuery("findBeer", BeerEntity.class)
                    .setParameter("id", id)
                    .setParameter("realmId", realm.getId())
                    .getSingleResult();
            return beer;
        } catch (NoResultException e) {
            throw new NotFoundException("Beer not found");
        }

    }

    private BeerRepresentation toRepresentation(BeerEntity entity) {

        BeerRepresentation rep = new BeerRepresentation();

        rep.setId(entity.getId());

        rep.setName(entity.getName());
        rep.setType(entity.getType());
        rep.setAbv(entity.getAbv());

        return rep;

    }

    @GET
    @Path("users/search-by-attr2")
    @NoCache
    @Produces({ org.keycloak.utils.MediaType.APPLICATION_JSON })
    @Encoded
    public List<UserDto> searchUsersByAttribute(@DefaultValue(defaultAttr) @QueryParam("attr") String attr,
                                                @QueryParam("value") String value) {

        logger.info("searchUsersByAttribute: attr= " + attr + " value= " +value );


        AppAuthManager authManager = new AppAuthManager();
        try {
           // logger.info("searchUsersByAttribute: Auth manager is null " + new Gson().toJson(authManager) );


           // String tokenString = authManager.extractAuthorizationHeaderToken(headers);

           /* if (tokenString == null) {
                logger.info("searchUsersByAttribute: tokenString is null "  );
            }*/

            //
        }catch (Exception e){
            logger.info("searchUsersByAttribute: Exception= " + e.getMessage() );
        }
        //

        //

        RealmModel realm = session.getContext().getRealm();
        // logger.info("searchUsersByAttribute: count= " + session.getContext().getClient().getName().toString() );

        Long count = session.users().getUsersStream(realm,false).count();
/*        Long count = session.users().getUsersStream(realm,true)
                .filter(x->x.getAttributes().keySet().stream().filter(y->y.equals("merchant_id")).findFirst().get().equals("1"))).count();*/
        logger.info("searchUsersByAttribute: count= " + count );

        //MapStorage<UUID, TestEntity, UserDto> userStore;
       /* ModelCriteriaBuilder<UserModel> mcb = KeycloakModelUtils.createCriteriaBuilder()
                .compare(UserModel.SearchableFields.REALM_ID, ModelCriteriaBuilder.Operator.EQ, realm.getId())
                .compare(UserModel.SearchableFields.ATTRIBUTE, ModelCriteriaBuilder.Operator.EQ, attr, value);
*/
       /* List<UserDto> userDtos = new ArrayList<>();
        userDtos.add(userDto);*/

        List<UserModel> userModels= session.users().searchForUserByUserAttribute(attr, value, session.getContext().getRealm())
                .stream().collect(Collectors.toList());

        List<UserDto> userDtos = new ArrayList<>();

        if(userModels!=null){
            logger.info("userModels: userModels= " + userModels.size() );
            UserDto userDto = new UserDto("akash","bastah","jdjd","11","em" +
                    "email","1");
            UserModel userModel = userModels.get(0);
            logger.info("userModel: userModel= {}" + userModel.getUsername() );

            userDtos.add(userDto);
        }

        return userDtos;

    }

}
