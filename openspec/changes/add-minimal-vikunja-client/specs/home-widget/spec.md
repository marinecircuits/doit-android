# Spec Delta

## Purpose

Shows open tasks on the home screen so they can be checked and ticked off, or a new one started, without opening the app first.

## ADDED Requirements

### Requirement: Widget content
The app SHALL provide a resizable home-screen widget that shows the open tasks in the same order and with the same due labels as the main list, scrolling when they do not fit, with a header containing the app name and a + button.

#### Scenario: Widget on home screen
- **WHEN** the user places the widget and has 12 open tasks
- **THEN** the widget lists them in list order and can be scrolled to see all 12

### Requirement: Complete from widget
Each task in the widget SHALL have a checkbox; checking it SHALL complete the task exactly as completing it in the app does (local first, synced later), and the task SHALL disappear from the widget.

#### Scenario: Tick off from home screen
- **WHEN** the user checks "Buy milk" in the widget
- **THEN** it disappears from the widget and from the app's list, and is done on the server after sync

### Requirement: Widget shortcuts
Tapping the widget's + button SHALL open the app with the capture box focused and the keyboard shown. Tapping a task title SHALL open the app.

#### Scenario: Add from widget
- **WHEN** the user taps + on the widget
- **THEN** the app opens with the capture box focused and the keyboard visible

### Requirement: Widget freshness
The widget SHALL update after every local change and after every sync, whether made in the app, in the widget or by a background sync.

#### Scenario: Task added in the web UI
- **WHEN** a task is created in the web UI and a background sync runs
- **THEN** the widget shows the new task without the app being opened

### Requirement: Widget when signed out
When the app is not connected to a server, the widget SHALL show a short message and open the setup screen when tapped.

#### Scenario: Widget before setup
- **WHEN** the widget is placed before the app has been set up
- **THEN** it reads "Open doit to connect" and tapping it opens the setup screen
