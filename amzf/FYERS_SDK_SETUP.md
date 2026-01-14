# FYERS SDK Setup Instructions

## Step 1: Download FYERS Java SDK

1. Visit the official FYERS Java SDK repository:
   - **GitHub**: https://github.com/fyersapi/fyers-java-sdk
   - Or contact FYERS support for the JAR file

2. Download `fyersjavasdk.jar` (usually from releases or provided by FYERS)

3. Place the JAR in this project's root directory:
   ```
   /Users/jnani/Desktop/AnnuPaper/annu-v04/fyersjavasdk.jar
   ```

## Step 2: Install SDK into Local Maven Repository

Run this command from the project root (where fyersjavasdk.jar is located):

```bash
cd /Users/jnani/Desktop/AnnuPaper/annu-v04

mvn deploy:deploy-file \
  -Durl="file:repo" \
  -Dfile=fyersjavasdk.jar \
  -DgroupId=com.tts.in \
  -DartifactId=fyersjavasdk \
  -Dpackaging=jar \
  -Dversion=1.0
```

This will create a local Maven repository in the `repo/` directory.

## Step 3: Verify Installation

After running the deploy command, you should see:

```
repo/
└── com/
    └── tts/
        └── in/
            └── fyersjavasdk/
                └── 1.0/
                    ├── fyersjavasdk-1.0.jar
                    └── fyersjavasdk-1.0.pom
```

## Step 4: Rebuild Project

The `pom.xml` has already been configured with:
- Dependency on `com.tts.in:fyersjavasdk:1.0`
- Local file repository pointing to `${project.basedir}/repo`
- Required `org.json` dependency

Simply rebuild:

```bash
mvn clean compile
```

## Step 5: Verify SDK Adapter is Active

Check logs for:
```
[FYERS] Using official FYERS SDK v3 adapter
[FYERS SDK] Market data socket initialized
```

## Fallback Behavior

If SDK is not available, the system will automatically fall back to the raw WebSocket implementation (already working with retry logic).

## Architecture

```
BrokerAdapterFactory
├─ FyersV3SdkAdapter (primary) ← uses official SDK
└─ FyersAdapter (fallback) ← raw WebSocket with retry
```

## Troubleshooting

**If SDK JAR is not found:**
- System logs: `[FACTORY] FYERS SDK not available - using fallback adapter`
- System continues with raw WebSocket implementation

**If Maven deploy fails:**
- Ensure `fyersjavasdk.jar` is in the project root
- Check Maven is installed: `mvn --version`
- Verify file permissions

**If compilation fails after adding SDK:**
- Check `pom.xml` dependency section
- Run `mvn dependency:tree` to see if SDK is loaded
- Verify local repo structure matches Step 3
