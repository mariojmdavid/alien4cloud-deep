package es.upv.deep.mockorchestrator;

import java.io.InputStream;
import java.util.Scanner;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@RestController("/")
@Slf4j
public class RestCtrlr {

  protected HttpHeaders headers;

  public RestCtrlr() {
    headers = new HttpHeaders();
    headers.add("Accept", "application/json");
  }

  @RequestMapping(value = "ping", method = RequestMethod.GET)
  public ResponseEntity<String> ping() {
    log.info("ping");
    return new ResponseEntity<String>("OK", headers, HttpStatus.OK);
  }

  @RequestMapping(value = "deployments", method = RequestMethod.POST)
  public ResponseEntity<String> deploy() {
    log.info("deploy");
    return new ResponseEntity<String>(readReply("/deployment_reply.json"), headers, HttpStatus.OK);
  }

  @RequestMapping(value = "deployments", method = RequestMethod.DELETE)
  public ResponseEntity<String> undeploy() {
    log.info("undeploy");
    return new ResponseEntity<String>("{}", headers, HttpStatus.NO_CONTENT);
  }

  @RequestMapping(value = "deployments", method = RequestMethod.GET)
  public ResponseEntity<String> getDeployment() {
    log.info("getDeployment");
    return new ResponseEntity<String>(readReply("/get_deployment_reply.json"), headers, HttpStatus.OK);
  }

  @RequestMapping(value = "authenticate", method = RequestMethod.GET)
  public ResponseEntity<String> authenticate() {
    log.info("authenticate");
    return new ResponseEntity<String>(readReply("/authentication_reply.json"), headers, HttpStatus.OK);
  }
  
  @RequestMapping(value = "authenticate", method = RequestMethod.POST)
  public ResponseEntity<String> authenticatePost() {
    log.info("authenticate post");
    return new ResponseEntity<String>(readReply("/authentication_reply.json"), headers, HttpStatus.OK);
  }


  protected String readReply(String replyResPath) {
    InputStream is = RestCtrlr.class.getResourceAsStream(replyResPath);
    Scanner sc = new Scanner(is);
    StringBuffer sb = new StringBuffer();
    while (sc.hasNextLine()) {
      sb.append(sc.nextLine());
    }
    return sb.toString();
  }


}