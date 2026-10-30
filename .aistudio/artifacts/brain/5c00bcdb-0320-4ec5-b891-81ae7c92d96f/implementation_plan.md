# Implementation Plan - XMPP MultiRoom Enhancements

This plan outlines the steps to add file transfer capabilities and prepare for voice/video calls in the XMPP MultiRoom application.

## 1. File Transfer (XEP-0363: HTTP File Upload)
We will implement the most common and robust way to share files in XMPP.

- **Smack Integration:**
    - Use `HttpFileUploadManager` from `smack-extensions` to check server support and upload files.
    - Handle slot requests and file uploads using `OkHttp`.
- **UI Updates:**
    - Add an "Attach File" button in `MultiRoomMainScreen`.
    - Implement a file picker using `ActivityResultContracts.GetContent`.
    - Create a `FileMessageBubble` component to show file transfers with download/open actions.
- **Persistence:**
    - Update `XmppViewModel` to handle file upload states and store file URLs in message history.

## 2. Voice and Video Calls (Groundwork)
Implementing full WebRTC is a significant task. We will start with the signalling part.

- **Jingle Signalling:**
    - Enable Jingle extensions in Smack.
    - Create a `CallOverlay` UI to show incoming/outgoing call states.
- **Future Integration:**
    - This will require a WebRTC library (like `google-webrtc`) in a future iteration to handle actual media streams.

## 3. User Experience & Aesthetics
- **Design:** Apply Material 3 guidelines for a polished feel.
- **Icons:** Use Material Symbols for file types (image, pdf, video, etc.).

## 4. "Download" Clarification
- For files: We will add a download button in the chat.
- For the app: We will remind the user that they can download the project APK/Source via the AI Studio sidebar menu (Settings > Export).

## Proposed Tasks
1. [ ] Add necessary permissions to `AndroidManifest.xml` (READ_EXTERNAL_STORAGE/Media permissions).
2. [ ] Implement `FileTransferManager` logic in `XmppViewModel`.
3. [ ] Create `FileMessageBubble` and integrate it into the chat list.
4. [ ] Implement file picking and uploading flow.
5. [ ] (Optional/Phase 2) Add Jingle call UI placeholders.
