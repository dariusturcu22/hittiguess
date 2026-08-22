from fastapi import FastAPI

from app.metadata.router import router as metadata_router

app = FastAPI(title="hitguessr AI microservice")
app.include_router(metadata_router)
