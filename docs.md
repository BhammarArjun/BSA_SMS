read for kotlin for android for google edge here -> refer further from inside page using internet -> (depricated) https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android

-> (new) https://developers.google.com/edge/litert-lm/android (see if NPU is used, MTP and multi modality , Tool usage, )


see which all models in gemma are available from huggingface -> https://huggingface.co/litert-community/models

also check for models from this -> 
https://huggingface.co/collections/facebook/mobilellm





https://huggingface.co/litert-community/Gemma3-1B-IT



https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm





or shall i use -> https://github.com/pytorch/executorch/blob/main/extension/llm/export/config/llm_config.py#L32

there it is -> i am intrested in qwen 3.5 0.8 B model, phi_4_mini, lfm2_5_350m



https://docs.pytorch.org/executorch/stable/intro-section.html

https://docs.pytorch.org/executorch/stable/llm/export-llm.html

https://docs.pytorch.org/executorch/stable/llm/run-on-android.html


https://unsloth.ai/docs/basics/inference-and-deployment/deploy-llms-phone



Do further research if you want - like for models like qwen and all. 

Now here is my Goal -> i want to make an App on android phone which have a good UI no doubt - it shows all my transactions from time of install to all future txns happening on phone - its SMS will come so i want them to pass through LLM to identify all info like price, date, counterparty, debit/credit, category of txn and some good visualizations. 

everytime new message comes - it passes to the small model for classification that if it is a bank txn credit or debit then only go to main model for classification and store the data lcally in some app db. 

You got this. ? so do a reaseach and make all the models available from size to framework - and make a good html visuzalization first so that i can review it and we can pick which LLMs to use. 