/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.sceneform.samples.solarsystem;

import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.Toast;
import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class SolarActivity extends AppCompatActivity {
  private static final int RC_PERMISSIONS = 0x123;
  private boolean installRequested;

  private GestureDetector gestureDetector;
  private Snackbar loadingMessageSnackbar = null;

  private ArSceneView arSceneView;

  private ModelRenderable sunRenderable;
  private ModelRenderable mercuryRenderable;
  private ModelRenderable venusRenderable;
  private ModelRenderable earthRenderable;
  private ModelRenderable lunaRenderable;
  private ModelRenderable marsRenderable;
  private ModelRenderable jupiterRenderable;
  private ModelRenderable saturnRenderable;
  private ModelRenderable uranusRenderable;
  private ModelRenderable neptuneRenderable;
  private ViewRenderable solarControlsRenderable;

  private final SolarSettings solarSettings = new SolarSettings();

  // True once scene is loaded
  private boolean hasFinishedLoading = false;

  // True once the scene has been placed
  private boolean hasPlacedSolarSystem = false;

  // Astronomical units to meters ratio. Used for positioning the planets of the solar system
  private static final float AU_TO_METERS = 0.5f;

  @Override
  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
//   CompletableFuture requires api level 24
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (!DemoUtils.checkIsSupportedDeviceOrFinish(this)) {
      // Not a supported device
      Log.e("DEBUG", "device not supported.");
      return;
    }

    setContentView(R.layout.activity_solar);
    arSceneView = findViewById(R.id.ar_scene_view);

    // Build all the planet models.
    CompletableFuture<ModelRenderable> sunStage =
        ModelRenderable.builder().setSource(this, Uri.parse("Sol.sfb")).build();
    CompletableFuture<ModelRenderable> mercuryStage =
        ModelRenderable.builder().setSource(this, Uri.parse("Mercury.sfb")).build();
    CompletableFuture<ModelRenderable> venusStage =
        ModelRenderable.builder().setSource(this, Uri.parse("Venus.sfb")).build();
    CompletableFuture<ModelRenderable> earthStage =
        ModelRenderable.builder().setSource(this, Uri.parse("Earth.sfb")).build();
    CompletableFuture<ModelRenderable> lunaStage =
        ModelRenderable.builder().setSource(this, Uri.parse("Luna.sfb")).build();
    CompletableFuture<ModelRenderable> marsStage =
        ModelRenderable.builder().setSource(this, Uri.parse("Mars.sfb")).build();
    CompletableFuture<ModelRenderable> jupiterStage =
        ModelRenderable.builder().setSource(this, Uri.parse("Jupiter.sfb")).build();
    CompletableFuture<ModelRenderable> saturnStage =
        ModelRenderable.builder().setSource(this, Uri.parse("Saturn.sfb")).build();
    CompletableFuture<ModelRenderable> uranusStage =
        ModelRenderable.builder().setSource(this, Uri.parse("Uranus.sfb")).build();
    CompletableFuture<ModelRenderable> neptuneStage =
        ModelRenderable.builder().setSource(this, Uri.parse("Neptune.sfb")).build();

    // Build a renderable from a 2D View.
    CompletableFuture<ViewRenderable> solarControlsStage =
        ViewRenderable.builder().setView(this, R.layout.solar_controls).build();

    CompletableFuture.allOf(
            sunStage,
            mercuryStage,
            venusStage,
            earthStage,
            lunaStage,
            marsStage,
            jupiterStage,
            saturnStage,
            uranusStage,
            neptuneStage,
            solarControlsStage)
        .handle(
            (notUsed, throwable) -> {

              if (throwable != null) {
                DemoUtils.displayError(this, "Unable to load renderable", throwable);
                return null;
              }

              // Sceneform loads Renderable resources in the background
              try {
                sunRenderable = sunStage.get();
                mercuryRenderable = mercuryStage.get();
                venusRenderable = venusStage.get();
                earthRenderable = earthStage.get();
                lunaRenderable = lunaStage.get();
                marsRenderable = marsStage.get();
                jupiterRenderable = jupiterStage.get();
                saturnRenderable = saturnStage.get();
                uranusRenderable = uranusStage.get();
                neptuneRenderable = neptuneStage.get();
                solarControlsRenderable = solarControlsStage.get();

                // success
                hasFinishedLoading = true;

              } catch (InterruptedException | ExecutionException ex) {
                DemoUtils.displayError(this, "Unable to load renderable", ex);
              }

              return null;
            });

    // gesture detector
    gestureDetector =
        new GestureDetector(
            this,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onSingleTapUp(MotionEvent e) {
                onSingleTap(e);
                return true;
              }

              @Override
              public boolean onDown(MotionEvent e) {
                return true;
              }
            });


    arSceneView
        .getScene()
        .setOnTouchListener(
            (HitTestResult hitTestResult, MotionEvent event) -> {
              // Case solar system hasn't been placed yet
              if (!hasPlacedSolarSystem) {
                // detect a tap
                return gestureDetector.onTouchEvent(event);
              }

              // Case solar system is created, we propagate the touch to the scene(plat cards, speed,..)
              return false;
            });

    // Request CAMERA permission
    DemoUtils.requestCameraPermission(this, RC_PERMISSIONS);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (arSceneView == null) {
      return;
    }

    if (arSceneView.getSession() == null) {
      // Case session wasn't created yet, we don't resume rendering.
      try {
        Session session = DemoUtils.createArSession(this, installRequested);
        if (session == null) {
          installRequested = DemoUtils.hasCameraPermission(this);
          return;
        } else {
          arSceneView.setupSession(session);
        }
      } catch (UnavailableException e) {
        DemoUtils.handleSessionException(this, e);
      }
    }

    try {
      arSceneView.resume();
    } catch (CameraNotAvailableException ex) {
      DemoUtils.displayError(this, "Unable to get camera", ex);
      finish();
      return;
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (arSceneView != null) {
      arSceneView.pause();
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (arSceneView != null) {
      arSceneView.destroy();
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
    if (!DemoUtils.hasCameraPermission(this)) {
      Toast.makeText(
              this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
              .show();
      finish();
    }
  }



  private void onSingleTap(MotionEvent tap) {
    if (!hasFinishedLoading) {
      // Solar system not created yet, we can't do anything
      return;
    }

    Frame frame = arSceneView.getArFrame();
    if (frame != null) {
      if (!hasPlacedSolarSystem && tryPlaceSolarSystem(tap, frame)) {
        hasPlacedSolarSystem = true;
      }
    }
  }

  private boolean tryPlaceSolarSystem(MotionEvent tap, Frame frame) {
    if (tap != null && frame.getCamera().getTrackingState() == TrackingState.TRACKING) {
      for (HitResult hit : frame.hitTest(tap)) {
        Trackable trackable = hit.getTrackable();
        if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
          // Create the Anchor.
          Anchor anchor = hit.createAnchor();
          AnchorNode anchorNode = new AnchorNode(anchor);
          anchorNode.setParent(arSceneView.getScene());
          Node solarSystem = createSolarSystem();
          anchorNode.addChild(solarSystem);
          return true;
        }
      }
    }

    return false;
  }

  private Node createSolarSystem() {
    Node base = new Node();

    Node sun = new Node();
    sun.setParent(base);
    sun.setLocalPosition(new Vector3(0.0f, 0.5f, 0.0f));

    Node sunVisual = new Node();
    sunVisual.setParent(sun);
    sunVisual.setRenderable(sunRenderable);
    sunVisual.setLocalScale(new Vector3(0.5f, 0.5f, 0.5f));

    Node solarControls = new Node();
    solarControls.setParent(sun);
    solarControls.setRenderable(solarControlsRenderable);
    solarControls.setLocalPosition(new Vector3(0.0f, 0.25f, 0.0f));

    View solarControlsView = solarControlsRenderable.getView();
    SeekBar orbitSpeedBar = solarControlsView.findViewById(R.id.orbitSpeedBar);
    orbitSpeedBar.setProgress((int) (solarSettings.getOrbitSpeedMultiplier() * 10.0f));
    orbitSpeedBar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            float ratio = (float) progress / (float) orbitSpeedBar.getMax();
            solarSettings.setOrbitSpeedMultiplier(ratio * 10.0f);
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {}
        });

    SeekBar rotationSpeedBar = solarControlsView.findViewById(R.id.rotationSpeedBar);
    rotationSpeedBar.setProgress((int) (solarSettings.getRotationSpeedMultiplier() * 10.0f));
    rotationSpeedBar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            float ratio = (float) progress / (float) rotationSpeedBar.getMax();
            solarSettings.setRotationSpeedMultiplier(ratio * 10.0f);
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {}
        });

    // Toggle the solar controls ON and OFF by tapping the sun.
    sunVisual.setOnTapListener(
        (hitTestResult, motionEvent) -> solarControls.setEnabled(!solarControls.isEnabled()));

    createPlanet("Mercury", sun, 0.4f, 47f, mercuryRenderable, 0.019f, 0.03f);

    createPlanet("Venus", sun, 0.7f, 35f, venusRenderable, 0.0475f, 2.64f);

    Node earth = createPlanet("Earth", sun, 1.0f, 29f, earthRenderable, 0.05f, 23.4f);

    createPlanet("Moon", earth, 0.15f, 100f, lunaRenderable, 0.018f, 6.68f);

    createPlanet("Mars", sun, 1.5f, 24f, marsRenderable, 0.0265f, 25.19f);

    createPlanet("Jupiter", sun, 2.2f, 13f, jupiterRenderable, 0.16f, 3.13f);

    createPlanet("Saturn", sun, 3.5f, 9f, saturnRenderable, 0.1325f, 26.73f);

    createPlanet("Uranus", sun, 5.2f, 7f, uranusRenderable, 0.1f, 82.23f);

    createPlanet("Neptune", sun, 6.1f, 5f, neptuneRenderable, 0.074f, 28.32f);

    return base;
  }

  private Node createPlanet(
      String name,
      Node parent,
      float auFromParent,
      float orbitDegreesPerSecond,
      ModelRenderable renderable,
      float planetScale,
      float axisTilt) {
    // Orbit rotates around the sun and is positioned relative to it, while the planet rotates around the orbit
    // this is what made the rotation effect possible (also will allow each planet to orbit at its own speed, given
    // it rotates on its own, instead of the sun (root node) rotating the whole system.
    RotatingNode orbit = new RotatingNode(solarSettings, true, false, 0);
    orbit.setDegreesPerSecond(orbitDegreesPerSecond);
    orbit.setParent(parent);

    // Create the planet and position it relative to the sun.
    Planet planet =
        new Planet(
            this, name, planetScale, orbitDegreesPerSecond, axisTilt, renderable, solarSettings);
    planet.setParent(orbit);
    planet.setLocalPosition(new Vector3(auFromParent * AU_TO_METERS, 0.0f, 0.0f));

    return planet;
  }
}
