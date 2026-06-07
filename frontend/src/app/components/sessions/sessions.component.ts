import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { BotSession, SessionCreateRequest } from '../../models/session.model';

@Component({
  selector: 'app-sessions',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './sessions.component.html',
  styleUrls: ['./sessions.component.scss']
})
export class SessionsComponent implements OnInit {
  sessions: BotSession[] = [];
  showCreateForm = false;
  loading = false;
  message = '';

  newSession: SessionCreateRequest = {
    sessionName: '',
    craAccessToken: '',
    craRefreshToken: '',
    ga: '',
    gaXgz: ''
  };

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.loadSessions();
  }

  loadSessions(): void {
    this.api.getAllSessions().subscribe(res => {
      if (res.success) {
        this.sessions = res.data;
      }
    });
  }

  createSession(): void {
    this.loading = true;
    this.message = '';
    this.api.createSession(this.newSession).subscribe({
      next: res => {
        this.loading = false;
        if (res.success) {
          this.message = 'Session created successfully!';
          this.showCreateForm = false;
          this.resetForm();
          this.loadSessions();
        } else {
          this.message = 'Error: ' + res.message;
        }
      },
      error: err => {
        this.loading = false;
        this.message = 'Error creating session: ' + err.message;
      }
    });
  }

  refreshSession(id: number): void {
    this.api.refreshSession(id).subscribe(res => {
      if (res.success) {
        this.loadSessions();
        this.message = 'Session refreshed';
      }
    });
  }

  deleteSession(id: number): void {
    if (confirm('Are you sure you want to delete this session?')) {
      this.api.deleteSession(id).subscribe(res => {
        if (res.success) {
          this.loadSessions();
          this.message = 'Session deleted';
        }
      });
    }
  }

  resetForm(): void {
    this.newSession = {
      sessionName: '',
      craAccessToken: '',
      craRefreshToken: '',
      ga: '',
      gaXgz: ''
    };
  }
}
