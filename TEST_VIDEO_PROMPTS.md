# Kola infant test-video prompts

Generate each clip as **photorealistic footage**, 16:9 MP4, 10–15 seconds, 24 or 30 FPS, no cuts, no slow motion, no captions, and no camera movement unless the prompt explicitly requests it. Keep the infant fully clothed and the scene calm. Add a small `TEST FOOTAGE` watermark if supported.

These clips test computer-vision behavior only. They do not validate medical conditions, breathing, or sleep safety.

## Core posture and pose coverage

### 1. Supine baseline — overhead

`Photorealistic nursery video from a fixed overhead crib camera. One infant lies centered on their back on a firm fitted mattress, face clearly visible, arms resting beside the torso, legs slightly bent. The infant makes small natural hand, foot, and head movements, then remains relaxed. Empty crib, even daylight, entire body visible, realistic proportions and motion.`

Tests: body detection, face visibility, low motion, pose confidence, bounding box stability and center tracking.

### 2. Prone posture — overhead

`Photorealistic video from a fixed overhead camera. One awake infant lies on their stomach during supervised tummy time on a firm clear surface, head turned to one side, elbows under the shoulders, legs extended. The infant gently raises and lowers the head and moves both forearms. Clear face profile, full body visible, bright diffuse daylight.`

Tests: prone pose, head orientation, elbow and shoulder landmarks, motion energy and face-confidence changes.

### 3. Left side-lying — side camera

`Photorealistic nursery footage from a camera placed beside the crib at mattress height and angled slightly downward. One infant lies on the left side with knees softly bent, one arm in front of the chest and the other partly behind the body. The infant turns the head toward the camera and gently moves one leg. Crib rails visible but not blocking the torso, stable lighting.`

Tests: side-lean classification, partial joint occlusion, side perspective and face visibility.

### 4. Right side-lying — opposite side camera

`Photorealistic video from a fixed camera on the opposite side of a crib, angled diagonally across the mattress. One infant lies on the right side with arms gathered near the chest and legs bent. The infant slowly stretches the upper arm, rotates the head away from the camera, then looks back. Full torso visible between crib rails.`

Tests: mirrored side posture, temporary facial occlusion, landmark recovery and temporal stability.

### 5. Rolling back to side

`Photorealistic overhead crib video. One infant begins lying on their back, raises both knees, turns the head and shoulders, and slowly rolls onto the left side. The movement is smooth and anatomically natural, with the full body visible throughout. Empty firm mattress, fixed camera, soft daylight.`

Tests: posture transition, pose velocity, body-center movement and temporal event logic.

### 6. Rolling side to stomach

`Photorealistic video from a fixed upper-corner nursery camera looking diagonally into the crib. One awake infant begins on their side and slowly rolls onto the stomach, then lifts the head and moves both arms. The entire body remains visible, crib rails appear at the image edges, stable warm lighting.`

Tests: diagonal viewpoint, roll transition, bounding-box consistency and pose-landmark continuity.

### 7. Independent sitting

`Photorealistic video from a fixed side camera positioned slightly above mattress level. One older infant sits upright near the middle of a clear playpen, legs in front, hands briefly touching the mattress for balance. The infant reaches left, returns upright, then reaches right. Full body visible, neutral background, bright indoor light.`

Tests: seated posture, torso angle, wrist tracking, center-of-mass movement and side-view inference.

### 8. Crawling across the frame

`Photorealistic nursery video from a fixed diagonal corner camera. One infant crawls slowly from the back-left of a clear play area toward the front-right, pauses, looks toward the camera, and continues. Hands, knees, face and torso remain visible for most of the clip. Realistic motion, stable daylight.`

Tests: crawling pose, movement intensity, tracking across the image and changing landmark scale.

### 9. Pulling to stand at crib rail

`Photorealistic video from a fixed side camera outside a crib. One older infant crawls toward the crib rail, grips it with both hands, rises through kneeling to a supported standing posture, then remains holding the rail. Show the full body and rail clearly, realistic proportions, steady indoor lighting.`

Tests: crawling-to-kneeling-to-standing transitions, vertical body-center change and rail occlusion.

### 10. Active limb movement

`Photorealistic overhead video. One awake infant lies on their back and naturally kicks both legs, waves both arms, briefly brings one hand toward the face, then settles. The head and torso stay near the center of a clear firm mattress. Fixed camera, bright even light, full body visible.`

Tests: high motion without boundary crossing, wrist/ankle landmarks, motion score range and false-alert resistance.

## Boundary and position testing

### 11. Slow safe-zone crossing

`Photorealistic video from a fixed overhead camera. One infant begins centered inside a clearly visible rectangular taped test boundary on a large padded play surface, then slowly crawls until the torso center crosses one edge and remains outside for at least four seconds. Unobstructed full-body view, even daylight.`

Tests: custom polygon crossing, two-second persistence threshold, body-center location and high-severity event sync.

### 12. Touch boundary without crossing

`Photorealistic overhead video. One infant lies inside a clearly marked polygon test zone, stretches one arm and one foot beyond the line, then pulls them back while the torso center remains inside. The infant never fully leaves the zone. Fixed camera, full body visible.`

Tests: resistance to false boundary alarms when only extremities cross.

### 13. Leave and re-enter zone

`Photorealistic diagonal nursery-camera footage. One crawling infant moves from the center of a marked safe area across its boundary, stays outside for four seconds, then crawls back inside and sits. Keep the body visible throughout, fixed camera, stable daylight.`

Tests: enter/exit state changes, event cooldown and recovery visualization.

### 14. Adult lifts infant from zone

`Photorealistic side-camera video of a clear crib. One infant lies centered inside a marked test zone. A caregiver enters the frame, safely supports the infant with both hands, lifts the infant upward and out of the crib, then exits the frame. Stable camera and lighting.`

Tests: rapid body-center exit, second-person interference, disappearing pose and event persistence.

## Visibility and occlusion testing

### 15. Head turns away

`Photorealistic overhead nursery video. One infant lies on their back with the face initially visible, slowly turns the head fully away from the camera for five seconds, then turns back. Arms and torso remain visible and nearly still. Empty mattress, fixed camera, even light.`

Tests: facial-landmark loss while body landmarks remain stable and face-visibility recovery.

### 16. Hand briefly covers face

`Photorealistic side-angle crib video. One awake infant lies on their back, raises both hands over the face for four seconds, lowers them, then looks toward the camera. Keep the torso and legs unobstructed. Fixed camera, soft daylight.`

Tests: temporary face occlusion, debounce timing and pose tracking through partial obstruction.

### 17. Crib rail occludes limbs

`Photorealistic video from a low side camera outside a crib. One infant crawls behind the vertical crib rails, causing the hands, knees and parts of the torso to be intermittently hidden while the head remains visible. The camera stays fixed and focused.`

Tests: fragmented landmark confidence, partial-body inference and bounding-box stability.

### 18. Partial frame exit

`Photorealistic corner-camera footage of a large playpen. One infant crawls toward the near edge until half of the body leaves the camera frame, pauses for four seconds, then moves back into full view. Stable lighting, no camera motion.`

Tests: cropped landmarks, body-center accuracy, confidence degradation and recovery.

### 19. Caregiver passes in front of camera

`Photorealistic fixed nursery-camera video. One infant remains lying calmly in a crib while a caregiver walks between the camera and crib, blocking most of the view for two seconds, then leaves. The infant becomes fully visible again. Stable exposure and.`

Tests: short whole-scene occlusion, prevention of premature alerts and inference recovery.

## Motion, stillness and negative controls

### 20. Extended visual stillness

`Photorealistic overhead video. One sleeping infant lies on their back on a clear firm mattress and remains visually still for the entire 15-second clip except for extremely subtle natural chest motion. Fixed camera, stable soft light, no moving background objects.`

Tests: extended-stillness timer. This is not a breathing or apnea test.

### 21. Movement burst then calm

`Photorealistic overhead crib video. One infant begins still, suddenly performs energetic but natural arm and leg movements for four seconds, then settles back into a calm position. The torso remains inside the center area. Fixed camera and stable daylight.`

Tests: motion spike, settling behavior, cooldown and no false boundary event.

### 22. Empty crib with moving shadows

`Photorealistic video of an empty crib from a fixed overhead camera. No infant or person appears. Soft shadows from a ceiling fan move repeatedly across the fitted mattress while every physical object remains stationary.`

Tests: negative control for person detection and resistance to background-motion false positives.

### 23. Empty crib with curtain motion

`Photorealistic side-camera nursery video showing an empty crib. A curtain in the distant background moves gently from airflow, but the crib and camera remain stationary. Daylight changes slightly as the curtain moves. No people.`

Tests: negative control, localized background motion and brightness variation.

### 24. Caregiver only

`Photorealistic fixed nursery-camera video. An adult caregiver leans over an empty crib, adjusts the fitted sheet, checks the mattress, and leaves. No infant appears at any time. Clear full scene, stable lighting.`

Tests: adult-versus-infant limitation, false body detection and event interpretation.

## Lighting and camera-integrity coverage

### 25. Low-light side view

`Photorealistic night nursery video from a fixed side camera with very dim warm light. One infant lies on their back, turns the head, and gently moves one arm. Preserve realistic low-light sensor noise and reduced contrast without switching to infrared. Full torso visible.`

Tests: brightness metric, pose confidence under noise and low-light motion detection.

### 26. Light turns off and on

`Photorealistic fixed overhead crib video. One infant is visible under normal room light for four seconds, the room light switches off leaving a very dark image for five seconds, then a dim night-light switches on and the infant becomes visible again. No camera movement.`

Tests: persistent-darkness event, brightness telemetry and recovery.

### 27. Camera is covered

`Photorealistic fixed nursery-camera video. One infant is initially visible in a crib. A cloth moves directly in front of the camera lens until the image becomes almost completely dark for five seconds, then the cloth is removed and the original view returns.`

Tests: camera-covered alert and restored monitoring state.

### 28. Camera is bumped

`Photorealistic nursery video. A fixed side camera initially shows one infant centered in a crib. Halfway through the clip, the camera is accidentally bumped, producing a rapid whole-frame shift and a new stable composition where the infant is near the edge. No edit or cut;.`

Tests: consecutive whole-frame motion, camera-shift event and landmark-coordinate recovery.

### 29. Gradual daylight change

`Photorealistic overhead crib video. One infant remains calmly on their back while daylight gradually becomes brighter across the entire 15-second shot, as if clouds move away from a window. The camera is fixed and the infant makes only small hand movements.`

Tests: brightness drift without camera-cover or motion false alarms.

## Hard cases and stress tests

### 30. Patterned clothing

`Photorealistic diagonal crib-camera video. One infant wears high-contrast patterned pajamas and rolls gently from back to side, then moves both legs. The mattress has a plain fitted sheet, full body visible, stable bright light.`

Tests: landmark robustness to clothing texture and posture transition.

### 31. Similar-colored clothing and sheet

`Photorealistic overhead video. One infant wears pale clothing close in color to the fitted mattress, creating low foreground-background contrast. The infant lies on the back, raises both arms, and turns to one side. Fixed camera, diffuse light.`

Tests: low-contrast pose confidence, bounding-box stability and motion sensitivity.

### 32. Two infants in view

`Photorealistic overhead play-area video with two infants fully visible and safely separated. One infant lies on the back moving both legs while the other sits and reaches for a toy outside the marked test zone. Fixed camera, bright even light.`

Tests: known single-person pose-model limitation, subject switching and unstable body-center behavior.

### 33. Fast crawling with motion blur

`Photorealistic side-camera video. One infant crawls quickly across a clear playpen from right to left, producing realistic mild motion blur in the hands and face, then stops and looks toward the camera. Fixed camera, indoor daylight.`

Tests: high motion, landmark confidence during blur and tracking recovery after stopping.

### 34. Wide camera view

`Photorealistic video from a fixed upper corner of a nursery showing the entire crib and surrounding room. One infant appears relatively small in the image, lies in the crib, rolls to one side, and moves both arms. No other people, stable daylight.`

Tests: minimum subject scale, expected pose-confidence reduction and camera-placement guidance.

### 35. Close camera crop

`Photorealistic side-camera video framed closely around one infant in a crib. The head, torso and arms are visible, but the lower legs occasionally fall outside the frame. The infant turns the head and reaches toward the camera. Stable light.`

Tests: partial-pose behavior, missing ankle landmarks and confidence reporting.

## Recommended hackathon sequence

Generate these first:

1. Prompt 1 — normal baseline
2. Prompt 5 — visible posture transition
3. Prompt 11 — confirmed safe-zone crossing
4. Prompt 12 — extremity crosses but torso remains inside
5. Prompt 16 — temporary face occlusion
6. Prompt 21 — movement burst without leaving the zone
7. Prompt 26 — darkness and recovery
8. Prompt 28 — camera-shift alert
9. Prompt 22 — empty-scene negative control

This sequence demonstrates annotations, metrics, alerts, recovery behavior, and false-positive resistance instead of showing only successful detections.

## Facial-expression and possible crying-cue coverage

### 36. Frontal crying expression

`Photorealistic close nursery-camera footage of one infant lying on their back with the face large, frontal, evenly illuminated, and fully visible. The infant begins calm with a closed mouth, then develops a clear crying facial expression: eyebrows draw inward, eyes narrow, cheeks tense, and the mouth opens widely for several seconds before relaxing again. Keep the head inside the frame and avoid hands covering the face.`

Tests: sustained mouth opening, low-smile probability, narrowed eyes, facial bounding box and possible crying-expression alert.

### 37. Crying expression from a side camera

`Photorealistic footage from a fixed camera beside the crib, angled slightly downward. One infant turns the face partly toward the camera and shows a crying facial expression with a tense brow, narrowed eyes, raised cheeks, and repeated wide mouth opening. The head moves gently from side to side while the torso remains visible and the lighting stays stable.`

Tests: facial-cue confidence at a moderate head angle, intermittent contour loss and temporal persistence.

### 38. Calm face to crying face to calm

`Photorealistic frontal crib-camera video of one infant with the face clearly visible. The infant looks calm for several seconds with relaxed eyes and a closed mouth, gradually begins crying with a furrowed brow and open mouth, maintains the expression, and then slowly settles back to a relaxed face.`

Tests: cue onset, sustained threshold, event creation, cooldown and recovery to a low score.

### 39. Yawning negative control

`Photorealistic close side-angle nursery video of one calm infant. The infant opens the mouth very widely for a natural yawn, closes the eyes briefly, finishes the yawn, and returns to a relaxed neutral expression. The brow and cheeks remain relaxed rather than tense, and the face stays fully visible.`

Tests: open-mouth false-positive resistance and the limitation of distinguishing yawning from crying visually.

### 40. Smiling and laughing negative control

`Photorealistic frontal nursery-camera footage of one awake infant looking toward the camera. The infant smiles broadly, opens the mouth during silent laughter, squints both eyes, waves both arms, and returns to a gentle smile. Use clear even lighting and keep the complete face visible.`

Tests: smile probability, open-mouth false-positive resistance and separation of positive expression from possible crying cues.

### 41. Babbling negative control

`Photorealistic close crib-camera footage of one calm infant facing the camera and silently babbling. The infant repeatedly opens and closes the mouth in several different shapes, moves the tongue and lips naturally, keeps relaxed eyebrows and open eyes, and occasionally smiles.`

Tests: repeated mouth motion without a sustained distress alert and temporal smoothing.

### 42. Crying face partially covered by hands

`Photorealistic overhead nursery video of one infant showing a crying facial expression while repeatedly bringing both hands in front of the mouth and eyes. The face alternates between fully visible, partly covered, and visible again. The head and torso remain in the frame under stable lighting.`

Tests: face-visibility loss, cue interruption, contour reacquisition and avoidance of stale scores during occlusion.

### 43. Face turns away during crying expression

`Photorealistic diagonal crib-camera footage of one infant whose face is initially frontal and shows a clear crying expression with a tense brow, narrowed eyes, and open mouth. The infant turns the head away until facial features cannot be measured, remains turned briefly, and then looks back toward the camera while calm.`

Tests: frontal-face requirement, score reset when the head turns, face-obscured event and recovery.

For the facial-cue demo, use prompts **36, 38, 39, 40 and 42**. Together they show positive detection, state transitions, yawning and smiling negative controls, and temporary facial occlusion.

