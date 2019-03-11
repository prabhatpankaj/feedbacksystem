package de.thm.ii.submissioncheck.controller

import java.net.UnknownHostException

import com.fasterxml.jackson.databind.JsonNode
import de.thm.ii.submissioncheck.misc.{BadRequestException, LDAPConnector, UnauthorizedException}
import de.thm.ii.submissioncheck.services.{LoginService, SettingService, UserService}
import javax.servlet.http.{Cookie, HttpServletRequest, HttpServletResponse}
import net.unicon.cas.client.configuration.{CasClientConfigurerAdapter, EnableCasClient}
import org.ldaptive.LdapEntry
import org.springframework.web.bind.annotation._
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.{Autowired, Value}

/**
  * LoginController simply perfoem login request. In future it might send also a COOKIE
  *
  * @author Benjamin Manns
  */
@RestController
@EnableCasClient
@RequestMapping(path = Array("/api/v1"))
class LoginController extends CasClientConfigurerAdapter {
  private final val LABEL_STUDENT_ROLE = 16
  private final val application_json_value = "application/json"
  @Autowired
  private val userService: UserService = null
  @Autowired
  private val settingService: SettingService = null
  @Value("${cas.client-host-url}")
  private val CLIENT_HOST_URL: String = null
  @Autowired
  private val loginService: LoginService = null
  private val logger = LoggerFactory.getLogger(this.getClass)
  private val LABEL_LOGIN_RESULT = "login_result"
  private val LABEL_SHOW_PRIVACY = "show_privacy"
  private val LABEL_AUTHORIZATION = "Authorization"
  private val LABEL_SUCCESS = "success"
  private val LABEL_USERNAME = "username"

  @Value("${ldap.url}")
  private implicit val LDAP_URL: String = null

  @Value("${ldap.basedn}")
  private implicit val LDAP_BASE_DN: String = null

  /**
    * Authentication starts here. here we using CAS
    *
    *
    * This Webservice sends user to CAS to perform a login. CAS redirects to this point and
    * here a answer to a connected Application (i.e. Angular) will be sent
    *
    * @author Benjamin Manns
    * @param route requested route by user, has to be forwarded to the Angular App
    * @param request Http request gives access to the http request information.
    * @param response HTTP Answer (contains also cookies)
    * @return Java Map
    */
  @RequestMapping(value = Array("login"), method = Array(RequestMethod.GET))
  def userLogin(@RequestParam(value = "route", required = false) route: String, request: HttpServletRequest, response: HttpServletResponse): Any = {
    try {
      val principal = request.getUserPrincipal
      var name: String = null
      if (principal == null) {
        logger.warn("HELP WE GOT NO ANSWER FOM CAS")
      } else {
        name = principal.getName
      }

      var existingUser = userService.loadUserFromDB(name)
      if (existingUser.isEmpty) {
        // Load more Infos from LDAP
        val entry = LDAPConnector.loadLDAPInfosByUID(name)(LDAP_URL, LDAP_BASE_DN)
        userService.insertUserIfNotExists(entry.getAttribute("uid").getStringValue, entry.getAttribute("mail").getStringValue,
          entry.getAttribute("givenName").getStringValue, entry.getAttribute("sn").getStringValue, LABEL_STUDENT_ROLE)
        existingUser = userService.loadUserFromDB(name)
      }
      val jwtToken = userService.generateTokenFromUser(existingUser.get)
      setBearer(response, jwtToken)

      val cookieMaxAge = 30
      val co = new Cookie("jwt", jwtToken)
      co.setPath("/")
      co.setHttpOnly(false)
      co.setMaxAge(cookieMaxAge)
      logger.info("route = " + route)
      response.addCookie(co)
      response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY)
      //response.setHeader("Location", CLIENT_HOST_URL + "/login?route=" + (if (route != null) route else ""))
      response.setHeader("Location", CLIENT_HOST_URL + "/login")
      "jwt"
    }
    catch {
      case e: Throwable => {
        logger.error("Error: ", e)
        response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY)
        response.setHeader("Location", CLIENT_HOST_URL + "/login")
        "error"
      }
    }
  }

  /**
    * Authentication starts here. We can load data directly from LDAP. Until now this is not possible
    *
    * @author Benjamin Manns
    * @param request Http request gives access to the http request information.
    * @param response HTTP Answer (contains also cookies)
    * @param jsonNode Request Body of User login
    * @return Java Map
    */
  @RequestMapping(value = Array("login/ldap"), method = Array(RequestMethod.POST))
  def userLDAPLogin(request: HttpServletRequest, response: HttpServletResponse, @RequestBody jsonNode: JsonNode): Map[String, Boolean] = {
    try {
    val username = jsonNode.get(LABEL_USERNAME).asText()
    val password = jsonNode.get("password").asText()
    var ldapUser: Option[LdapEntry] = None

    try {
      ldapUser = LDAPConnector.loginLDAPUserByUIDAndPassword(username, password)(LDAP_URL, LDAP_BASE_DN)
    } catch {
      case _: Exception => ldapUser = None
    }

    val login: Boolean = ldapUser.isDefined
    if (!login) {
      // If LDAP Fails, we try to load from guest account
      val guestUser = userService.guestLogin(username, password)
      val jwtToken: String = if (guestUser.isDefined) this.userService.generateTokenFromUser(guestUser.get) else null
      setBearer(response, jwtToken)
      Map(LABEL_SUCCESS -> guestUser.isDefined)
    } else {
      userService.insertUserIfNotExists(ldapUser.get.getAttribute("uid").getStringValue, ldapUser.get.getAttribute("mail").getStringValue,
        ldapUser.get.getAttribute("givenName").getStringValue, ldapUser.get.getAttribute("sn").getStringValue, LABEL_STUDENT_ROLE)

      val user = userService.loadUserFromDB(username)
      val jwtToken: String = if (login) this.userService.generateTokenFromUser(user.get) else null
      setBearer(response, jwtToken)
      Map(LABEL_SUCCESS -> login)
    }
  } catch {
        case e: NullPointerException => {
          throw new BadRequestException("Please provide: username and password")
        }
    }
  }

  private def setBearer(response: HttpServletResponse, token: String) = response.addHeader(LABEL_AUTHORIZATION, "Bearer " + token)

  /**
    * Give information if user is already successful loged in
    * @author Benjamin Manns
    * @param request Http request gives access to the http request information.
    * @param response HTTP Answer (contains also cookies)
    * @return JSON if user is logged in
    */
  @RequestMapping(value = Array("login/check"), method = Array(RequestMethod.POST))
  def checkIfUsersIsLogedIn(request: HttpServletRequest, response: HttpServletResponse): Map[String, Boolean] = {
    val user = userService.verifyUserByHeaderToken(request)
    Map(LABEL_SUCCESS -> user.isDefined)
  }

  /**
    * User can accept the privacy policy
    * @author Benjamin Manns
    * @param request Http request gives access to the http request information.
    * @param response HTTP Answer (contains also cookies)
    * @param jsonNode Request Body contains json
    * @return JSON if update worked out
    * @return
    */
  @RequestMapping(value = Array("login/privacy/accept"), method = Array(RequestMethod.POST))
  def privacyAcceptanceOfUser(request: HttpServletRequest, response: HttpServletResponse, @RequestBody jsonNode: JsonNode): Map[String, Boolean] = {
    try {
      val username = jsonNode.get(LABEL_USERNAME).asText()
      Map(LABEL_SUCCESS -> userService.acceptPrivacyForUser(username))
    }
    catch {
      case e: NullPointerException => {
        throw new BadRequestException("Please provide: username")
      }
    }
  }

  /**
    * check if user has to accept privacy first
    * @author Benjamin Manns
    * @param request Http request gives access to the http request information.
    * @param response HTTP Answer (contains also cookies)
    * @param jsonNode Request Body contains json
    * @return simple answer if user need to accept privacy policy
    */
  @RequestMapping(value = Array("login/privacy/check"), method = Array(RequestMethod.POST))
  def checkUsersPrivacyAcceptance(request: HttpServletRequest, response: HttpServletResponse, @RequestBody jsonNode: JsonNode): Map[String, Boolean] = {
    try {
      val settingsPrivacyShow = settingService.loadSetting("privacy.show")
      val show = (if (settingsPrivacyShow.isDefined) settingsPrivacyShow.get.asInstanceOf[Boolean] else true)

      if (!show) {
        Map(LABEL_SUCCESS -> true)
      } else {
        val username = jsonNode.get(LABEL_USERNAME).asText()
        val user = userService.loadUserFromDB(username)
        if(user.isEmpty) {
          Map(LABEL_SUCCESS -> false)
        } else {
          Map(LABEL_SUCCESS -> user.get.privacy_checked)
        }
      }
    }
    catch {
      case e: NullPointerException => {
        throw new BadRequestException("Please provide: username")
      }
    }
  }
}
