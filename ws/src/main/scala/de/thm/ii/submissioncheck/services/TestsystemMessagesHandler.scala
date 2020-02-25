package de.thm.ii.submissioncheck.services
import java.io.{File, FileOutputStream}
import java.util.{Timer, TimerTask}

import de.thm.ii.submissioncheck.misc.JsonParser
import org.apache.commons.codec.binary.Base64
import org.slf4j.{Logger, LoggerFactory}
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.beans.factory.annotation.{Autowired, Value}
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component

/**
  * Class / Service which handles incoming message with payload of a testsytem
  */
@Component
class TestsystemMessagesHandler {
  @Value("${compile.production}")
  private val compile_production: Boolean = true
  @Autowired
  private val taskService: TaskService = null
  @Autowired
  private val taskExtensionService: TaskExtensionService = null
  private val LABEL_TASK_ID = "taskid"
  private val LABEL_USER_ID = "userid"
  private val LABEL_SUCCESS = "success"
  private val logger: Logger = LoggerFactory.getLogger(classOf[TestsystemMessagesHandler])
  private val LABEL_CHECKER_SERVICE_NOT_ALL_PARAMETER = "Checker Service did not provide all parameters"
  private var storageService: StorageService = null

  /**
    * Using autowired configuration, they will be loaded after self initialization
    */
  def configurateStorageService(): Unit = {
    this.storageService = new StorageService(compile_production)
  }

  /**
    * After autowiring initialize storage service
    * @return timer run
    */
  @Bean
  def importStorageProcessor: SmartInitializingSingleton = () => {
    /** wait 3 seconds to be sure everything is connected like it should*/
    val bean_delay = 300
    new Timer().schedule(new TimerTask() {
      override def run(): Unit = {
        configurateStorageService
      }
    }, bean_delay)
  }

  private def base64Decode(raw: String, output: File) = {
    val decoded = Base64.decodeBase64(raw)
    val out = new FileOutputStream(output)
    try out.write(decoded)
    finally if (out != null) out.close()
  }

  /**
    * handles incoming zip file of plagiat processing
    * @param testsystem testsystemid
    * @param data payload
    */
  def plagiatPackedZip(testsystem: String, data: String): Unit = {
    val answeredMap = JsonParser.jsonStrToMap(data)
    val userid = answeredMap(LABEL_USER_ID).toString.toInt
    val taskid = answeredMap(LABEL_TASK_ID).toString.toInt
    val zipRaw = answeredMap("zip").toString

    // save file from base64
    val dirPath = storageService.getAndMakeTaskExtensionsPath(userid, "plagiat_zip", taskid)
    val zipPath = dirPath.resolve("plagiat_zip.zip")
    base64Decode(zipRaw, zipPath.toFile)

    // store info in DB
    taskExtensionService.setTaskExtension(taskid, userid, "plagiatPackedZip", zipPath.toString, "file")
  }

  /**
    * handles plagiat markers
    * @param testsystem testsystemid
    * @param data payload
    * @return nothing
    */
  def plagiarismcheckerAnswer(testsystem: String, data: String): AnyVal = {
    val answeredMap = JsonParser.jsonStrToMap(data)

    try {
      logger.warn(answeredMap.toString())

      val taskId = answeredMap(LABEL_TASK_ID).toString.toInt

      val submissionlist = answeredMap("submissionlist").asInstanceOf[List[Map[String, Boolean]]]
      for (submission <- submissionlist) {
        taskService.setPlagiatPassedForSubmission(submission.keys.head, submission.values.head)
      }
    } catch {
      case _: NoSuchElementException => logger.warn(LABEL_CHECKER_SERVICE_NOT_ALL_PARAMETER)
    }
  }
}
