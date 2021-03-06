import {Injectable} from '@angular/core';
import {Observable, Subject, BehaviorSubject, Subscription} from 'rxjs';
import {ConferenceInvitation, ConfInvite, Ticket, User} from '../interfaces/HttpInterfaces';
import {RxStompClient} from '../util/rx-stomp';
import {UserService} from './user.service';
import {Message} from 'stompjs';
import {ConferenceService} from './conference.service';

/**
 * Service that provides observables that asynchronacally updates tickets, users and privide invitations to take
 * part in a conference.
 */
@Injectable({
  providedIn: 'root'
})
export class ClassroomService {
  private users: Subject<User[]>;
  private tickets: Subject<Ticket[]>;
  private invitations: Subject<ConferenceInvitation>;
  private openConferences: BehaviorSubject<ConferenceInvitation[]>;
  private invitationSubscriptions: Subscription[] = [];
  private courseId = 0;
  private invitation: ConferenceInvitation;

  private stompRx: RxStompClient = null;

  public constructor(private user: UserService, private conferenceService: ConferenceService) {
    this.users = new BehaviorSubject<User[]>([]);
    this.tickets = new BehaviorSubject<Ticket[]>([]);
    this.invitations = new Subject<ConferenceInvitation>();
    this.openConferences = new BehaviorSubject<ConferenceInvitation[]>([]);
    this.conferenceService.getConferenceInvitation().subscribe(n => this.invitation = n);
  }

  /**
   * @return Users of the connected course.
   */
  public getUsers(): Observable<User[]> {
    return this.users.asObservable();
  }

  public getInvitationFromUser(user: User): ConferenceInvitation {
    return this.openConferences.value.find((invitation) => invitation.creator.username == user.username);
  }
  public hasOpenConference(user: User) {
   return this.openConferences.value.findIndex((invitation) => invitation.creator.username == user.username) != -1;
  }
  /**
   * @return Tickets of the connected course.
   */
  public getTickets(): Observable<Ticket[]> {
    return this.tickets.asObservable();
  }

  /**
   * @return Get invitations to take part in a conference
   */
  public getInvitations(): Observable<ConferenceInvitation> {
    return this.invitations.asObservable();
  }

  /**
   * @return True if service is connected to the backend.
   */
  public isJoined() {
    return this.stompRx && this.stompRx.isConnected();
  }

  /**
   * Connect to backend
   * @param courseId The course, e.g., classroom id
   * @return Observable that completes if connected.
   */
  public join(courseId: number): Observable<void> {
    this.courseId = courseId;
    this.stompRx = new RxStompClient('https://feedback.mni.thm.de/websocket');
    return new Observable<void>(c => {
      this.stompRx.connect(this.constructHeaders()).subscribe(_ => {

        // Handles invitation from tutors / docents to take part in a webconference
        this.listen('/user/' + this.user.getUsername() + '/classroom/invite').subscribe(m => this.handleInviteMsg(m));
        this.listen('/user/' + this.user.getUsername() + '/classroom/users').subscribe(m => this.handleUsersMsg(m));
        this.listen('/topic/classroom/' + this.courseId + '/left').subscribe(m => this.requestUsersUpdate());
        this.listen('/topic/classroom/' + this.courseId + '/joined').subscribe(m => this.requestUsersUpdate());
        this.requestUsersUpdate();
        this.listen('/user/' + this.user.getUsername() + '/classroom/tickets').subscribe(m => this.handleTicketsMsg(m));
        this.listen('/topic/classroom/' + this.courseId + '/ticket/create').subscribe(m => this.requestTicketsUpdate());
        this.listen('/topic/classroom/' + this.courseId + '/ticket/update').subscribe(m => this.requestTicketsUpdate());
        this.listen('/topic/classroom/' + this.courseId + '/ticket/remove').subscribe(m => this.requestTicketsUpdate());

        this.listen('/topic/classroom/' + this.courseId + '/conference/opened').subscribe(m => this.requestConferenceUpdate());
        this.listen('/topic/classroom/' + this.courseId + '/conference/closed').subscribe(m => this.requestConferenceUpdate());
        this.listen('/user/' + this.user.getUsername() + '/classroom/conferences').subscribe(m => this.handleConferenceMsg(m));
        this.requestTicketsUpdate();
        this.joinCourse();

        c.next();
        c.complete();
      }, c.error);
    });
  }

  /**
   * Disconnects from the endpoint.
   * @return Observable that completes when disconnected.
   */
  public leave(): Observable<void> {
    return this.stompRx.disconnect(this.constructHeaders());
  }

  /**
   * Invites user to a conference by following the link provided as href.
   * @param href The link of the conference server
   * @param users The users to invite
   */
  public inviteToConference(invitation: ConferenceInvitation, users: { username: string; prename: string; surname: string }[]) {
    this.send('/websocket/classroom/invite', {invitation: invitation, users, 'courseid': this.courseId});
  }

  /**
   * Creates a new ticket.
   * @param ticket The ticket to create.
   */
  public createTicket(ticket: Ticket) {
    ticket.courseId = this.courseId;
    this.send('/websocket/classroom/ticket/create', ticket);
  }

  /**
   * Updates an existing ticket.
   * @param ticket The ticket to update.
   */
  public updateTicket(ticket: Ticket) {
    this.send('/websocket/classroom/ticket/update', ticket);
  }

  /**
   * Removes an existing ticket.
   * @param ticket The ticket to remove.
   */
  public removeTicket(ticket: Ticket) {
    this.send('/websocket/classroom/ticket/remove', ticket);
  }

  private handleInviteMsg(msg: Message) {
    this.invitations.next(JSON.parse(msg.body));
  }

  private handleUsersMsg(msg: Message) {
    this.users.next(JSON.parse(msg.body));
  }

  private handleTicketsMsg(msg: Message) {
    this.tickets.next(JSON.parse(msg.body));
  }

  private requestUsersUpdate() {
    this.send('/websocket/classroom/users', {courseId: this.courseId});
  }

  private requestTicketsUpdate() {
    this.send('/websocket/classroom/tickets', {courseId: this.courseId});
  }

  private joinCourse() {
    this.send('/websocket/classroom/join', {courseId: this.courseId});
  }

  private constructHeaders() {
    return {'Auth-Token': this.user.getPlainToken()};
  }

  private send(topic: string, body: {}): void {
    this.stompRx.send(topic, body, this.constructHeaders());
  }

  private listen(topic: string): Observable<Message> {
    return this.stompRx.subscribeToTopic(topic, this.constructHeaders());
  }

  public openConference(courseId) {
    this.send('/websocket/classroom/conference/opened', {invitation: this.invitation, courseId: courseId});
  }

  public closeConference(courseId) {
    this.send('/websocket/classroom/conference/closed', {invitation: this.invitation, courseId: courseId});
  }

  private requestConferenceUpdate() {
    this.requestUsersUpdate();
    this.send('/websocket/classroom/conferences', {courseId: this.courseId});
  }

  private handleConferenceMsg(msg: Message) {
    this.openConferences.next(JSON.parse(msg.body));
  }
}
