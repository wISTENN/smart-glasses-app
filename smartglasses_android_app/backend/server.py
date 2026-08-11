from flask import Flask, request, jsonify
import os

app = Flask(__name__)

@app.route('/transcribe', methods=['POST'])
def transcribe():
    file = request.files.get('file')
    if not file:
        return jsonify({"error": "No file uploaded"}), 400

    file_path = os.path.join('/tmp', 'voice_input.wav')
    file.save(file_path)

    # Здесь потом подставить Gemini / Whisper
    # Временно — заглушка, чтобы проект был рабочим по структуре.
    return jsonify({
        "transcript": "Привет, это тестовая команда с Bluetooth-гарнитуры.",
        "answer": "Голосовой помощник готов. Здесь можно подключить Gemini или Whisper."
    })

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8000)
