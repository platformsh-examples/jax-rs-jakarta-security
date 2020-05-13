package sh.platform.sample.security.oauth2;

import jakarta.nosql.mapping.keyvalue.KeyValueTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import sh.platform.sample.security.SecurityService;
import sh.platform.sample.security.User;
import sh.platform.sample.security.UserNotAuthorizedException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;

@ApplicationScoped
class Oauth2Service {

    static final int EXPIRE_IN = 3600;

    static final Duration EXPIRES = Duration.ofSeconds(EXPIRE_IN);

    @Inject
    private SecurityService securityService;

    @Inject
    @ConfigProperty(name = "keyvalue")
    private KeyValueTemplate template;

    @Inject
    private Validator validator;

    public Oauth2Response token(Oauth2Request request) {

        final Set<ConstraintViolation<Oauth2Request>> violations = validator.validate(request, Oauth2Request
                .GenerateToken.class);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        final User user = securityService.findBy(request.getUsername(), request.getPassword());
        final UserToken userToken = template.get(request.getUsername(), UserToken.class)
                .orElse(new UserToken(user.getName()));

        final Token token = Token.generate();

        AccessToken accessToken = new AccessToken(token, user.getName());
        RefreshToken refreshToken = new RefreshToken(userToken, token, user.getName());

        template.put(refreshToken, EXPIRES);
        template.put(Arrays.asList(userToken, accessToken));

        final Oauth2Response response = Oauth2Response.of(accessToken, refreshToken, EXPIRE_IN);
        return response;
    }

    public Oauth2Response refreshToken(Oauth2Request request) {
        final Set<ConstraintViolation<Oauth2Request>> violations = validator.validate(request, Oauth2Request
                .RefreshToken.class);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        RefreshToken refreshToken = template.get(request.getRefreshToken(), RefreshToken.class)
                .orElseThrow(() -> new UserNotAuthorizedException("Invalid Token"));

        final UserToken userToken = template.get(request.getUsername(), UserToken.class)
                .orElse(new UserToken(refreshToken.getUser()));

        final Token token = Token.generate();
        AccessToken accessToken = new AccessToken(token, refreshToken.getUser());
        refreshToken.update(accessToken, userToken, template);
        template.put(accessToken, EXPIRES);
        final Oauth2Response response = Oauth2Response.of(accessToken, refreshToken, EXPIRE_IN);
        return response;
    }

}
