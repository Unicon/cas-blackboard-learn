package net.unicon.blackboard.authentication;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jasig.cas.client.authentication.AttributePrincipal;
import org.jasig.cas.client.validation.Assertion;
import org.jasig.cas.client.validation.Cas20ServiceTicketValidator;
import org.jasig.cas.client.validation.TicketValidator;

import blackboard.data.user.User;
import blackboard.persist.PersistenceException;
import blackboard.persist.user.UserDbLoader;
import blackboard.platform.config.ConfigurationService;
import blackboard.platform.log.LogServiceFactory;
import blackboard.platform.security.authentication.BaseAuthenticationModule;
import blackboard.platform.security.authentication.BbAuthenticationFailedException;
import blackboard.platform.security.authentication.BbCredentialsNotFoundException;
import blackboard.platform.security.authentication.BbSecurityException;
import blackboard.platform.security.authentication.IUserPassAuthModule;
import blackboard.util.Base64Codec;

public class CasAuthenticationModule extends BaseAuthenticationModule implements
		IUserPassAuthModule {
	private static final String DISABLE_CAS_PARAMETER_NAME = "disableCAS";

	private static final String CAS_DEFAULT_LOGIN_PARAM = "/login?";

	private static final String CAS_DEFAULT_LOGOUT_PARAM = "/logout";

	private static final String[] CAS_PROP_KEYS = new String[] { "url",
			"redirect_url", "service", "logout_completely", "impl",
			"lookup_user_in_BB", "use_challenge" };
	public static final String DEFAULT_CAS_AUTH_TYPE = "cas";

	private static final String CAS_DEFAULT_SERVICE_PARAM = "service";
	private static final String CAS_DEFAULT_GATEWAY_PARAM = "gateway";
	private static final String CAS_DEFAULT_TICKET_PARAM = "ticket";

	private boolean casLogoutCompletely;

	private boolean casLookupUserInBB;

	private String casRedirectUrl;

	private String casService;

	private String casUrl;

	private void debug(final String str) {

		if (_logger == null)
			System.out.println(str);
		else
			_logger.logWarning(str);
	}

	private void debug(final String str, final Exception t) {
		if (_logger == null)
			t.printStackTrace();
		else
			_logger.logWarning(str, t);
	}

	@Override
	public String doAuthenticate(final HttpServletRequest request,
			final HttpServletResponse response) throws BbSecurityException,
			BbAuthenticationFailedException, BbCredentialsNotFoundException {

		debug("In <doAuthenticate>");
		boolean useCASAuthentication = shouldUseCasAuthentication(request);

		if (!useCASAuthentication) {
			debug("Custom "
					+ getAuthType()
					+ " authentication is disabled. Executing default blackboard authentication...");
			String user = super.doAuthenticate(request, response);

			debug("Executed default blackboard authentication. Returned:"
					+ user);

			return user;
		}

		final String ticket = getCasTicketParameterValue(request);

		if (ticket == null || ticket.equals("")) {
			debug("Cannot validate without a ticket. Aborting...");
			return null;
		}

		final TicketValidator stv = new Cas20ServiceTicketValidator(casUrl);

		String uid = null;
		Assertion assertion = null;

		try {
			debug("Validating CAS ticket " + ticket + " for service "
					+ casService);
			assertion = stv.validate(ticket, casService);
		} catch (final Exception e) {
			debug("CAS failed to validate service ticket " + ticket
					+ " for service " + casService, e);
			throw new BbAuthenticationFailedException(e.getMessage(), e);
		}

		try {

			if (assertion == null)
				throw new BbAuthenticationFailedException(
						"Can not validate service ticket " + ticket
								+ ". Assertion received from CAS is null");

			final AttributePrincipal principal = assertion.getPrincipal();
			if (principal == null)
				throw new Exception("Can not validate service ticket " + ticket
						+ ". Principal object retrieved from assertion is null");

			uid = principal.getName();
		} catch (final Exception e) {
			throw new BbAuthenticationFailedException(e.getMessage());
		}

		debug("Authenticated user " + uid);

		if (isCasLookupUserInBB()) {
			debug("Retrieving authenticated user " + uid
					+ " from blackboard...");

			final User user = getDBUserByUserName(uid);

			if (user != null) {
				request.setAttribute("userInDB", "true");
				debug("Found user "
						+ uid
						+ " in blackboard. Set request attribute 'userInDB' to true.");
			} else {
				request.setAttribute("userInDB", "false");
				debug("Did not find user "
						+ uid
						+ " in blackboard. Set request attribute 'userInDB' to false.");
			}

		}

		debug("Returning authenticated user " + uid);

		return uid;
	}

	private boolean shouldUseCasAuthentication(final HttpServletRequest request) {
		boolean useCAS = true;

		Enumeration<?> en = request.getParameterNames();

		while (en.hasMoreElements()) {
			String pName = en.nextElement().toString();
			
			if (pName.equalsIgnoreCase(DISABLE_CAS_PARAMETER_NAME)) {
				debug("Received parameter: " + pName + ". CAS authentication is flagged to be disabled.");
				useCAS = false;
			}
		}

		if (!useCAS)
			useCAS = getCasTicketParameterValue(request) != null;

		debug("Using CAS Authentication for request: " + useCAS);
		return useCAS;
	}

	private String getCasTicketParameterValue(final HttpServletRequest request) {
		final String ticket = request.getParameter(CAS_DEFAULT_TICKET_PARAM);
		debug("Received CAS ticket parameter:" + ticket);
		return ticket;
	}

	@Override
	public void doLogout(final HttpServletRequest request,
			final HttpServletResponse response) throws BbSecurityException {

		debug("In <doLogout>");
		boolean shouldUseCAS = shouldUseCasAuthentication(request);

		debug("Executing blackboard logout protocol...");
		super.doLogout(request, response);

		String redirectUrl = null;
		try {
			if (isCasLogoutCompletely() && shouldUseCAS) {

				redirectUrl = getCasUrl() + CAS_DEFAULT_LOGOUT_PARAM;
				if (getCasRedirectUrl() != null
						&& getCasRedirectUrl().length() != 0)
					redirectUrl = redirectUrl + "?url="
							+ encode(getCasRedirectUrl());

				debug("Logging out from CAS using redirect url: " + redirectUrl);
				response.sendRedirect(redirectUrl);
			} else {

				if (shouldUseCAS) {
					debug("Redirecting to CAS login page " + getCasUrl());
					redirectUrl = getCasUrl();
					response.sendRedirect(redirectUrl);
				} else
					debug("Not using CAS. Proceeding with default logout flow");

			}
		} catch (final Exception e) {
			throw new BbSecurityException("Can not reach the logout page at "
					+ redirectUrl + ": " + e.getMessage());
		}
	}

	private final String encode(final String src) {
		final String defaultEncoding = Charset.defaultCharset().name();
		String encoded = src;

		try {
			encoded = URLEncoder.encode(src, defaultEncoding);
		} catch (final UnsupportedEncodingException e) {
			debug(e.getMessage(), e);
		}
		return encoded;
	}

	@Override
	public String getAuthType() {
		return DEFAULT_CAS_AUTH_TYPE;
	}

	private String getCasRedirectUrl() {
		return casRedirectUrl;
	}

	private String getCasService() {
		return casService;
	}

	private String getCasUrl() {
		return casUrl;
	}

	/*
	 * Given <code>username</code>, returns user object from database. If user
	 * not in database, returns null
	 */
	private User getDBUserByUserName(final String userName) {
		User user;

		try {
			user = UserDbLoader.Default.getInstance().loadByUserName(userName);
		} catch (final Exception e) {
			return null;
		}
		return user;
	}

	private String getProperty(final String key) {
		final Object val = _config.getProperty(key);
		if (val == null)
			return "";
		else
			return val.toString();
	}

	@Override
	public String[] getPropKeys() {
		return CAS_PROP_KEYS;
	}

	@Override
	public User getUserFromUsernamePassword(final String userName,
			final String password) throws PersistenceException,
			BbAuthenticationFailedException, BbSecurityException {

		debug("In <getUserFromUsernamePassword>");

		debug("Looking up blackboard user " + userName
				+ " from blackboard database...");
		final String validatedUsername = authenticate(userName,
				Base64Codec.encode(password), null, false);
		if (validatedUsername == null) {
			debug("User " + userName
					+ " could not be authenticated. Returning null...");
			return null;
		}

		debug("Validated user is:" + validatedUsername);

		User user = null;
		try {
			user = getDBUserByUserName(validatedUsername);
		} catch (final Exception e) {
			debug("User "
					+ userName
					+ " could not be retrieved from blackboa. Returning null...",
					e);
			user = null;
		}

		return user;
	}

	@Override
	public void init(final ConfigurationService cfg) {
		super.init(cfg);

		_logger = LogServiceFactory.getInstance();

		debug("Initializing custom " + getAuthType()
				+ " authentication module settings...");

		setCasUrl(getProperty("url"));
		setCasService(getProperty("service"));
		setCasRedirectUrl(getProperty("redirect_url"));

		setCasLogoutCompletely("true"
				.equalsIgnoreCase(getProperty("logout_completely")));

		setCasLookupUserInBB("true"
				.equalsIgnoreCase(getProperty("lookup_user_in_BB")));

		debug(toString());
		debug("Initialized custom " + getAuthType()
				+ " authentication module settings.");
	}

	private boolean isCasLogoutCompletely() {
		return casLogoutCompletely;
	}

	private boolean isCasLookupUserInBB() {
		return casLookupUserInBB;
	}

	@Override
	public void requestAuthenticate(final HttpServletRequest request,
			final HttpServletResponse response) throws BbSecurityException {

		debug("In <requestAuthenticate>");

		boolean useCAS = shouldUseCasAuthentication(request);

		if (!useCAS) {
			debug("Custom "
					+ getAuthType()
					+ " authentication is disabled on blackboard root home. Executing default blackboard authentication request...");
			super.requestAuthenticate(request, response);
			return;
		}

		final Object errorMsgAttr = request.getAttribute("msg");
		String errorMsg = null;

		if (errorMsgAttr != null)
			errorMsg = (String) errorMsgAttr;

		final Object userInDbAttr = request.getAttribute("userInDB");
		String userInDb = null;

		if (userInDbAttr != null)
			userInDb = (String) userInDbAttr;

		if (errorMsg == null && userInDb != null && "false".equals(userInDb))
			errorMsg = "Unable to retrieve user record from the blackboard database.";

		if (errorMsg != null) {
			try {
				debug(errorMsg);
				response.sendError(401, errorMsg);
			} catch (final Exception e) {
				throw new BbSecurityException(e.getMessage());
			}
			return;
		}

		final String service = CAS_DEFAULT_SERVICE_PARAM + "="
				+ encode(getCasService());

		final String ticket = getCasTicketParameterValue(request);

		String gateway = null;
		if (ticket != null && ticket.length() > 0)
			gateway = CAS_DEFAULT_GATEWAY_PARAM + "=true";

		debug("CAS gateway parameter is set to " + gateway);

		final StringBuilder builder = new StringBuilder(getCasUrl());
		builder.append(CAS_DEFAULT_LOGIN_PARAM);
		builder.append(service);

		if (gateway != null) {
			builder.append("&");
			builder.append(gateway);
		}

		final String redirectUrl = builder.toString();
		try {
			debug("Redirecting to CAS to authenticate at:" + redirectUrl);

			response.sendRedirect(redirectUrl);
		} catch (final Exception e) {
			throw new BbSecurityException(" Can't reach login url "
					+ redirectUrl + ": " + e.getMessage());
		}
	}

	private void setCasLogoutCompletely(final boolean casLogoutCompletely) {
		this.casLogoutCompletely = casLogoutCompletely;
	}

	private void setCasLookupUserInBB(final boolean casLookupUserInBB) {
		this.casLookupUserInBB = casLookupUserInBB;
	}

	private void setCasRedirectUrl(final String casRedirectUrl) {
		this.casRedirectUrl = casRedirectUrl;
	}

	private void setCasService(final String casService) {
		this.casService = casService;
	}

	private void setCasUrl(final String casUrl) {
		this.casUrl = casUrl;
	}

	@Override
	public String toString() {
		return "CasAuthenticationModule [casUrl=" + casUrl + ", casService="
				+ casService + ", casRedirectUrl=" + casRedirectUrl
				+ ", casLogoutCompletely=" + casLogoutCompletely
				+ ", casLookupUserInBB=" + casLookupUserInBB + "]";
	}

}
