# MoveScan Estimator — Android test build

Native Android prototype for a professional moving estimator.

## Working in this build

- Customer, origin, destination, Pack Date and Load Date fields
- Sample mileage calculation with an editable mileage result
- Room selection and room-organized inventory
- Real video/audio recording through the device camera app
- Voice notes through Android speech recognition
- Furniture catalog lookup with standard cube and weight values
- Going / not-going inventory status
- ARCore availability check with a manual-measurement fallback
- Local on-device persistence for job information and inventory

## Prototype limits

The build records real video and audio. Automatic object recognition, transcript-to-inventory extraction, true ARCore dimension measurement, live routing mileage, and PDF export require production service integration and further device testing.

## Build

Push to `main`. The included GitHub Actions workflow produces `MoveScan-Android-Test-APK` as a downloadable workflow artifact.
