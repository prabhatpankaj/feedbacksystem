import {Component, Inject, OnInit} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {DatabaseService} from '../../../../service/database.service';
import {MatSnackBar} from '@angular/material/snack-bar';

@Component({
  selector: 'app-incoming-call-dialog',
  templateUrl: './incoming-call-dialog.component.html',
  styleUrls: ['./incoming-call-dialog.component.scss']
})
export class IncomingCallDialogComponent implements OnInit {
  participants: any[];
  conferenceURL: string;
  constructor(public dialogRef: MatDialogRef<IncomingCallDialogComponent>, private db: DatabaseService,
              @Inject(MAT_DIALOG_DATA) public data: any, private snackBar: MatSnackBar) { }
  audio;
  ngOnInit(): void {
    this.participants = this.data.participants;
    this.conferenceURL = this.data.conferenceURL;

    const notification = new Notification('Konferenzeinladung Feedbacksystem', {body: 'Sie werden zu einem Konferenzanruf eingeladen.'});
    this.audio = new Audio();
    this.audio.src = '../../../../assets/classic_phone.mp3';
    this.audio.load();
    this.audio.play();
    this.dialogRef.afterClosed().subscribe(next => {
      this.audio.pause();
      this.audio.currentTime = 0;
    });
    document.addEventListener('visibilitychange', function() {
      if (document.visibilityState === 'visible') {
        // The tab has become visible so clear the now-stale Notification.
        notification.close();
      }
    });
  }

  public acceptCall() {
    // todo notification api benutzen um auf invite hinzuweisen
    this.openUrlInNewWindow(this.conferenceURL);
    this.dialogRef.close();
  }

  public declineCall() {
    this.dialogRef.close();
  }

  openUrlInNewWindow(url: string) {
    window.open(url, '_blank');
  }
}
