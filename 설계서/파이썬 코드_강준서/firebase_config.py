import firebase_admin
from firebase_admin import credentials, db

# Firebase 서비스 계정 키 경로
cred = credentials.Certificate("serviceAccountKey.json")

# DB URL (Firebase > Realtime Database > URL 확인)
firebase_admin.initialize_app(cred, {
    'databaseURL': 'https://aico-1853c-default-rtdb.firebaseio.com/'
})
