package de.thm.ii.submissioncheck.controller

import java.util
import com.fasterxml.jackson.databind.JsonNode
import de.thm.ii.submissioncheck.misc.{BadRequestException, UnauthorizedException}
import de.thm.ii.submissioncheck.model.User
import de.thm.ii.submissioncheck.services.{CourseService, UserService}
import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation._

@RestController
@RequestMapping(path = Array("/api/v1/courses"))
class CourseController {

  /** Class field to perform JWT Auth*/
  private val userService: UserService = new UserService()

  private val courseService: CourseService = new CourseService()

  /**
    * getAllCourses is a route for all courses
    * @param request Request Header containing Headers
    * @return JSON
    */
  @RequestMapping(value = Array(""), method = Array(RequestMethod.GET))
  def getAllCourses(request:HttpServletRequest ): util.List[util.Map[String, String]] = {
    // TODO If admin -> all, if prof -->
    val user:User = userService.verfiyUserByHeaderToken(request)
    if(user == null)
      {
        throw new UnauthorizedException
      }
    courseService.getCoursesByUser(user)
  }

  /**
    * createCourse is a route to create a course
    * @param request contain request information
    * @param jsonNode contains JSON request
    * @return JSON
    */
  @RequestMapping(value = Array(""), method = Array(RequestMethod.POST), consumes = Array("application/json"))
  def createCourse(request:HttpServletRequest,@RequestBody jsonNode:JsonNode): util.Map[String, String] = {
    // TODO: nothing done yet, we need a service
    try {
      val name = jsonNode.get("name").asText()
      val description = jsonNode.get("description").asText()
      val task_typ = jsonNode.get("task_typ").asText()
    }
    catch {
      case e: NullPointerException => {
        throw new BadRequestException("Please provide: name, description, task_typ")
      }
    }
    val user:User = userService.verfiyUserByHeaderToken(request)
    if(user == null)
    {
      throw new UnauthorizedException
    }
    user.asJavaMap()
  }

  /**
    * getCourse provides course details for a specific course by given id
    * @param courseid unique course identification
    * @param request Request Header containing Headers
    * @return JSON
    */
  @RequestMapping(value = Array("/{id}"), method = Array(RequestMethod.GET), consumes = Array("application/json"))
  @ResponseBody
  def getCourse(@PathVariable("id") courseid: String, request:HttpServletRequest): util.Map[String, String] = {
    // If admin -> all, if prof -->
    val user:User = userService.verfiyUserByHeaderToken(request)
    if(user == null)
    {
      throw new UnauthorizedException
    }
    courseService.getCourseDetailes(courseid, user)
  }

}
