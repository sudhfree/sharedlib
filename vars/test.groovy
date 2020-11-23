call()
{

def agent= null
pipeline
{
agent any

parameters{
string(description: 'Enter the branch', name: 'branch1')
}

stages{
stage("setting the agent")
{
  steps{
  script{
  agent = env.branch1
  }
  }
}
}

}

pipeline
{
agent any

parameters{
string(description: 'Enter the repo', name: 'repo1')
}

stages{
stage("setting the agent")
{
  steps{
  echo env.repo
  }
}
}



}