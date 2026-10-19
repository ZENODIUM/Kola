# Kola product and vision research

> Archive note: Kola's current demo intentionally removed all learned models. References to pose, facial or audio models below are competitor/research context, not implemented functionality. See `README.md` for the shipped feature set.

## Product thesis

Kola should compete as a **privacy-first, explainable edge safety layer**, not as another video call. Incumbents already cover live A/V, multi-device access, lullabies, logs, and generic motion alerts. The differentiator is contextual event detection that combines a user-drawn spatial zone, pose landmarks, scene motion, confidence, and persistence over time.

This build deliberately calls events “signals” rather than diagnoses. It does not claim breathing, apnea, SIDS prevention, or medical-grade posture classification.

## What is implemented

| Capability | Technique | Runs where |
|---|---|---|
| Live camera | CameraX preview + `ImageAnalysis` latest-frame backpressure | Android |
| Body landmarks | Bundled ML Kit pose model, stream mode | Android, offline |
| Motion | Downsampled luminance frame differencing | Android, offline |
| Custom safe zone | User-drawn normalized polygon + ray-casting containment | Android, offline |
| Face visibility signal | Temporal visibility of nose/eye pose landmarks | Android, offline |
| Extended stillness | Motion threshold plus a time window | Android, offline |
| Camera cover/shift signal | Scene luminance + consecutive whole-frame motion spikes | Android, offline |
| Possible crying expression | Face contours + smile/eye classifications + temporal debounce | Android, offline |
| Test videos | Android document picker + frame extraction + same ML pipeline | Android, offline |
| Cross-platform sync | Small JSON status/events over LAN; Server-Sent Events to browser | Android + web |
| Evidence trail | Time-stamped, severity-coded event feed | Android + web |

## Highest-value next features

1. **Crib scene audit** — fine-tune a small YOLO/RT-DETR or EfficientDet-Lite model for pillows, blankets, toys, adult hands, pets, and crib rails. Combine object boxes with the safe-zone mask; an object outside the crib is irrelevant.
2. **Stronger camera calibration** — the prototype detects persistent darkness and large consecutive frame shifts; upgrade this with ORB feature matching or a homography against the calibrated crib reference.
3. **Sleep-state timeline, not a binary label** — use a temporal model over motion energy, pose velocity, cry likelihood, and light level; expose confidence and “unknown.”
4. **Audio event classifier** — YAMNet or a MobileNet audio classifier for cry, cough, glass break, smoke alarm, adult speech, and silence. Run 0.96-second windows locally and debounce them.
5. **Pose quality gate** — an infant-specific keypoint model is essential before posture claims. Adult pose models are a prototype overlay, because infant proportions, self-occlusion, blankets, and top-down views are out of distribution.
6. **Multi-signal alert voting** — require agreement, such as outside-zone + sustained motion or face-obscured + pose rotation, and attach the exact reasons to each alert.
7. **Privacy-preserving replay** — save only a short encrypted ring buffer locally; on an event, keep 5 seconds before and after. Make replay opt-in with automatic deletion.
8. **Caregiver handoff** — presence/status from several caregivers, escalation only when nobody acknowledges, and a “checking now” button to avoid duplicate responses.
9. **Environmental fusion** — Bluetooth temperature/humidity/CO2 and smoke-alarm audio, correlated with video but clearly separated from visual inference.
10. **WebRTC talkback** — authenticated signaling, DTLS-SRTP media, acoustic echo cancellation, push-to-talk, and a hardware volume ceiling. This should be implemented as a real channel, not a cosmetic control.

## Hackathon story

Lead with “calibrate once, understand context locally.” Draw the polygon live, load synthetic clip 3, let the body center cross the boundary, and show the web event arrive without transmitting video. Then disconnect Wi-Fi to show on-device detection continues. Reconnect and demonstrate status recovery. Judges can see the algorithm, the privacy boundary, and the failure-aware language.

## Validation plan

Create a small labeled evaluation matrix: 20 clips per condition, split by lighting, camera angle, clothing, skin tone, occlusion, and motion speed. Measure event precision/recall, false alarms per monitoring hour, time-to-alert, phone temperature, battery drain, and p50/p95 inference latency. Keep synthetic footage for engineering coverage, but validate on consented real-world data before any safety claim.

## Safety and technical evidence

- Android recommends CameraX `ImageAnalysis` for CPU-accessible frames and a keep-latest strategy when inference cannot match camera FPS: https://developer.android.com/media/camera/camerax/analyze
- ML Kit’s pose detector exposes 33 landmarks and confidence values, but it is beta and its guidance expects the subject to occupy a meaningful part of the image: https://developers.google.com/ml-kit/vision/pose-detection/android
- Infant-pose research exists because adult-to-infant domain transfer is difficult; AggPose and SyRIP/FiDIP are useful starting points: https://arxiv.org/abs/2205.05277 and https://arxiv.org/abs/2010.06100
- The AAP advises back sleeping on a firm, flat, non-inclined surface without soft bedding, and warns that consumer monitors do not prevent SIDS or replace safe-sleep practice: https://www.healthychildren.org/English/ages-stages/baby/sleep/Pages/A-Parents-Guide-to-Safe-Sleep.aspx
- Bibino already offers live video, two-way audio, replay, motion notifications, multiple devices, lullabies, and multi-baby support, so those are parity features rather than the pitch: https://bibinoapp.com/en

## Important limitations

- A single RGB camera cannot measure oxygen saturation and should not present visual micro-motion as clinical breathing detection.
- Generic pose landmark confidence is not infant posture confidence.
- Synthetic clips help reproduce edge cases but do not establish real-world safety performance.
- The included LAN server is a hackathon sync layer. Before internet deployment, add authentication, TLS, device pairing, encrypted persistence, rate limits, and threat modeling.
