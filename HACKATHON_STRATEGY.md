# Kola hackathon differentiation and research brief

> Current product decision (October 2026): the demo build is classical computer vision only. Model-based items below are retained as comparative research or a future opt-in roadmap; they are not implemented or claimed by the APK.

## One-line pitch

Kola turns an old Android phone into a privacy-first nursery observer that explains *why* it raised an alert: infant pose, joint motion, facial visibility, visual cry cues, an arbitrary caregiver-drawn safe zone, camera integrity, and a persistent evidence history—all processed on-device.

## What is already implemented

- CameraX live inference and direct video-demo inference with identical annotations.
- ML Kit 33-landmark pose tracking and face contours.
- Confidence-aware skeleton rendering, facial bounds, body center, pose confidence, scene motion, joint-only motion, left/right movement balance, visual crying-expression cue, brightness, contrast, latency, and FPS.
- A 3–16 vertex arbitrary polygon that remains open while editing and activates only after explicit completion.
- Temporal alerts for safe-zone crossing, sustained missing face, prolonged stillness, camera movement, low light/low contrast lens obstruction, and sustained visual cry cues.
- Persistent on-device History with filters and summary metrics.
- Android and web trend charts. Only telemetry and events sync; camera frames stay on the Android device.

## What prior research says

### Generic adult pose estimation is not enough by itself

Most general pose systems are trained around adult proportions. The 2024 STAPose3D paper explicitly identifies the shortage of large infant datasets and shows that infant fine-tuning plus temporal/multi-view learning improves video pose estimation: https://doi.org/10.1016/j.media.2024.103208

The babyPose dataset provides clinically supervised joint annotations from depth recordings of 16 preterm infants. It is valuable for research and benchmarking, but its NICU depth-camera domain differs from ordinary RGB nursery phones: https://pmc.ncbi.nlm.nih.gov/articles/PMC7551984/

A large infant-movement study evaluated 1,424 clinical and home videos and reported pose localization approaching human inter-rater spread. The important product lesson is to adapt and validate the model on infant video rather than treating a generic adult confidence score as ground truth: https://doi.org/10.1016/j.compmedimag.2021.102012

A 2024/2025 comparison found that generic models are not automatically worse than specialized models, but retraining a generic model on the target infant data significantly improves accuracy. It also recommends including top-down views in evaluation: https://pubmed.ncbi.nlm.nih.gov/40204781/

### Temporal movement is a stronger differentiator than a posture label

Research using 503 smartphone videos fine-tuned DeepLabCut on infant landmarks and then analyzed trajectories, reaching body-point errors comparable to annotator disagreement. This supports Kola's joint-motion history and future infant-specific adapter rather than a single-frame “sleeping/awake” classifier: https://pmc.ncbi.nlm.nih.gov/articles/PMC10883563/

The InfAct work introduced a public benchmark of 200 annotated home videos and modeled actions as start, transition, and end posture states. It reported 78% accuracy over ten infant actions, supporting a temporal action layer for roll, crawl, sit, stand, reach, and transition events: https://openaccess.thecvf.com/content/CVPR2023W/L3D-IVU/html/Huang_Posture-Based_Infant_Action_Recognition_in_the_Wild_With_Very_Limited_CVPRW_2023_paper.html

### Classical computer vision complements ML

OpenCV's Lucas–Kanade and Farnebäck optical-flow methods measure pixel motion between frames. This is useful for motion direction, camera-shift rejection, and movement heatmaps when pose temporarily fails: https://docs.opencv.org/4.x/d4/dee/tutorial_optical_flow.html

OpenCV MOG2/KNN background subtraction creates foreground masks for a mostly fixed camera. In Kola it could distinguish motion inside the crib from a whole-camera move, but it needs warm-up and should be disabled when the camera itself moves: https://docs.opencv.org/4.10.0/d1/dc5/tutorial_background_subtraction.html

OpenCV contour and point-polygon operations are appropriate for arbitrary safe zones. Kola currently uses an equivalent ray-casting point-in-polygon algorithm and includes concave and six-vertex unit tests: https://docs.opencv.org/4.10.0/d7/da8/tutorial_table_of_content_imgproc.html

### Audio should be independent corroborating evidence

YAMNet produces frame-level probabilities for 521 AudioSet classes and uses a MobileNetV1 architecture. A strong next step is an on-device transfer-learned head for infant cry, cough, adult speech, smoke alarm, glass break, and silence—not pretending a visual facial cue confirms sound: https://www.tensorflow.org/hub/tutorials/yamnet

TensorFlow's transfer-learning guide shows how to reuse YAMNet embeddings for a smaller task-specific classifier: https://www.tensorflow.org/tutorials/audio/transfer_learning_audio

### Real-time constraints

Google recommends ML Kit's base pose model in stream mode, lower-resolution frames with a sufficiently large subject, dropping frames while inference is busy, and CameraX `KEEP_ONLY_LATEST`. Kola follows these rules. ML Kit returns 33 landmarks, but the API remains beta and should not be presented as a medical guarantee: https://developers.google.com/ml-kit/vision/pose-detection/android

## Winning product angle

The strongest story is not “another phone camera.” It is **explainable, multi-signal, private context**:

1. **Separate scene motion from infant joint motion.** A bumped camera can create huge pixel motion while pose-relative joint motion stays low. An infant moving against a stable background creates both. Kola now exposes both metrics.
2. **Movement balance, not diagnosis.** Left/right movement balance can make the demo visually compelling, but must be described as a session observation—not a neurological assessment.
3. **Confidence-aware alerts.** Every alert requires persistence over time. Low pose confidence should reduce certainty rather than trigger dramatic claims.
4. **Arbitrary spatial rules.** A caregiver can outline a crib, mattress, play mat, doorway, or unsafe subregion rather than accepting a fixed rectangle.
5. **Evidence history.** Judges can see what happened, when, which signals supported it, and how the scene changed before the event.
6. **Metadata-only synchronization.** The web dashboard receives compact metrics and incidents, not nursery frames.

## Recommended next model modules

### Highest value

1. **Infant action classifier over landmark windows**
   - Input: 3–5 seconds of normalized pose landmarks plus visibility masks.
   - Classes: still, roll transition, crawl, sit, pull-to-stand, standing, reaching, face-down/face-away uncertainty.
   - Architecture: small 1D temporal convolution, GRU, or temporal graph network exported to TFLite.
   - Advantage: interpretable and much cheaper than processing full video again.

2. **Audio corroboration model**
   - YAMNet embeddings plus a small infant-specific classifier.
   - Combine visual and audio signals only after independent debouncing.
   - Show `visual cue`, `audio cue`, and `combined confidence` separately.

3. **Optical-flow fallback**
   - Track Shi–Tomasi features with pyramidal Lucas–Kanade.
   - Estimate global camera motion with RANSAC; subtract it before measuring crib-region motion.
   - Continue showing motion direction when pose landmarks are temporarily occluded.

4. **Infant-specific pose adapter**
   - Fine-tune an approved pose model on properly licensed RGB infant data and the exact camera angles used in the demo.
   - Benchmark top-down and diagonal/side views separately.
   - Export a quantized TFLite model and keep the generic model as a fallback.

### Visually strong demo features

- Motion trail/heatmap for the last 10 seconds.
- “Why this alert?” card listing duration, zone distance, pose confidence, scene motion, joint motion, face visibility, and cry/audio cues.
- Playback scrubber that jumps to event timestamps when local recording is explicitly enabled.
- Camera setup coach: subject size, blur/contrast, angle, darkness, obstruction, and safe-zone coverage.
- Multi-zone rules: safe sleeping area, crib rail proximity, caregiver entry area, and ignored regions.
- Per-session summaries: active minutes, longest quiet interval, zone crossings, face-visibility percentage, and camera-quality percentage.

## Evaluation plan judges can understand

Use a fixed test matrix and report results rather than saying “AI detected it”:

| Scenario | Expected evidence |
|---|---|
| Calm close-up | Face tracked; no full-body claim; stable visual cue |
| Top-down roll | Pose/joint-motion rise; roll transition candidate |
| Side-view crawl | Pose track plus directional movement |
| Pull to stand | Vertical body-axis change and standing candidate |
| Cross custom six-point boundary | Outside state persists, then one boundary event |
| Camera covered with dark cloth | Low brightness/contrast camera-integrity event |
| Camera covered with bright paper | Low contrast catches what darkness alone misses |
| Camera physically bumped | High global scene motion but lower joint-relative motion |
| Face turned away | Sustained face-not-visible event, not a cry claim |
| Crying face, silent video | Visual cue only; explicitly not audio-confirmed |

## Claims to avoid

- Do not claim breathing, apnea, suffocation, SIDS prevention, pain diagnosis, neurological diagnosis, or guaranteed safety.
- Do not label a visual facial expression as confirmed crying.
- Do not claim generic pose-confidence values are calibrated for infants.
- Do not retain or upload nursery video without explicit consent and clear retention controls.

## Suggested final demo sequence

1. Launch a side-view crawling video directly into Kola.
2. Show aligned pose, face bounds, scene motion, joint motion, balance, and latency.
3. Open Trends while inference continues in the background.
4. Return to Monitor and draw a five- or six-point concave zone; emphasize that it is not limited to a rectangle or triangle.
5. Let the body center cross it and wait for the debounced event.
6. Open History, filter to Safety, and show that the event persists after restarting the app.
7. Open the web dashboard and show the same telemetry without a video upload.
8. Close with the roadmap: infant-specific temporal action model, audio corroboration, and optical-flow camera-motion compensation.
